package ee.revkassa.service;

import ee.revkassa.infrastructure.config.RevkassaProperties;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.mail.MailMessage;
import ee.revkassa.infrastructure.mail.Mailer;
import ee.revkassa.infrastructure.security.PasswordService;
import ee.revkassa.infrastructure.security.TokenService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.passwordtoken.PasswordToken;
import ee.revkassa.persistence.passwordtoken.PasswordTokenPurposeEnum;
import ee.revkassa.persistence.passwordtoken.PasswordTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * One-time links for setting a password: INVITE (new account, 7 days) and RESET ("Forgot password?", 1 hour).
 * The link holds a random token; the database keeps only its SHA-256 hash. A member has at most one open link:
 * sending a new one or using one closes all others. Tokens and passwords are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordLinkService {

    private static final String SET_PASSWORD_PATH = "/set-password?token=";

    private final TokenService tokenService;
    private final PasswordService passwordService;
    private final PasswordTokenRepository passwordTokenRepository;
    private final Mailer mailer;
    private final RevkassaProperties properties;
    private final Clock clock;

    /**
     * Saves the link and sends it by email in one transaction: if the mail server fails, nothing is saved and
     * the member's previous link still works.
     *
     * @param createdBy the admin who sends an invite, null when the member asked for a reset
     * @throws ee.revkassa.infrastructure.mail.MailSendFailedException when the email could not be sent
     */
    @Transactional
    public void send(Member member, PasswordTokenPurposeEnum purpose, Member createdBy) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        closeOpenLinks(member, now);
        String token = tokenService.createToken();
        passwordTokenRepository.save(createPasswordToken(member, purpose, createdBy, token, now));
        mailer.send(createEmail(member, purpose, token));
        log.info("Password link sent: memberId={}, purpose={}, createdBy={}",
                member.getId(), purpose, createdBy != null ? createdBy.getId() : null);
    }

    /**
     * Does not log in: the member logs in with the new password afterwards.
     */
    @Transactional
    public void setPassword(String token, String newPassword) {
        PasswordToken passwordToken = getValidPasswordToken(token);
        Member member = passwordToken.getMember();
        member.setPasswordHash(passwordService.encode(newPassword));
        closeOpenLinks(member, OffsetDateTime.now(clock));
        log.info("Password set with link: memberId={}, purpose={}", member.getId(), passwordToken.getPurpose());
    }

    /**
     * Closes the member's open links, e.g. when the admin changes the email address they were sent to.
     */
    @Transactional
    public void cancelOpenLinks(Member member) {
        closeOpenLinks(member, OffsetDateTime.now(clock));
    }

    private void closeOpenLinks(Member member, OffsetDateTime now) {
        passwordTokenRepository.findAllOpenByMemberId(member.getId())
                .forEach(openToken -> openToken.setUsedAt(now));
    }

    private PasswordToken createPasswordToken(Member member, PasswordTokenPurposeEnum purpose, Member createdBy,
                                              String token, OffsetDateTime now) {
        PasswordToken passwordToken = new PasswordToken();
        passwordToken.setMember(member);
        passwordToken.setTokenHash(tokenService.hash(token));
        passwordToken.setPurpose(purpose);
        passwordToken.setExpiresAt(now.plus(purpose.getValidity()));
        passwordToken.setCreatedBy(createdBy);
        passwordToken.setCreatedAt(now);
        return passwordToken;
    }

    private MailMessage createEmail(Member member, PasswordTokenPurposeEnum purpose, String token) {
        String link = createLink(token);
        return switch (purpose) {
            case INVITE -> new MailMessage(member.getEmail(), "RevKassa: määra oma parool", """
                    Tere, %s!

                    Sulle on loodud RevKassa konto. Parooli määramiseks ava see link (kehtib 7 päeva):
                    %s

                    Seejärel saad sisse logida oma e-posti ja parooliga. Baaritahvlis saad edasi kasutada oma PIN-koodi.

                    Kui sa seda kirja ei oodanud, võid selle lihtsalt kustutada.
                    """.formatted(member.getDisplayName(), link));
            case RESET -> new MailMessage(member.getEmail(), "RevKassa: parooli lähtestamine", """
                    Tere, %s!

                    Keegi (loodetavasti sina) palus RevKassa parooli lähtestamist. Uue parooli määramiseks ava see link (kehtib 1 tund):
                    %s

                    Kui sa seda ei palunud, ignoreeri seda kirja: sinu parool jääb samaks.
                    """.formatted(member.getDisplayName(), link));
        };
    }

    private String createLink(String token) {
        String appUrl = properties.appUrl().replaceAll("/+$", "");
        return appUrl + SET_PASSWORD_PATH + token;
    }

    // Unknown, used and expired links and inactive members all get the same answer
    private PasswordToken getValidPasswordToken(String token) {
        PasswordToken passwordToken = passwordTokenRepository.findForUpdateByTokenHash(tokenService.hash(token))
                .orElseThrow(() -> invalidLink("unknown", null));
        if (passwordToken.getUsedAt() != null) {
            throw invalidLink("used", passwordToken);
        }
        if (!passwordToken.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw invalidLink("expired", passwordToken);
        }
        if (!passwordToken.getMember().getActive()) {
            throw invalidLink("member not active", passwordToken);
        }
        return passwordToken;
    }

    private static ForbiddenException invalidLink(String reason, PasswordToken passwordToken) {
        log.warn("Password link refused: reason={}, tokenId={}", reason, passwordToken != null ? passwordToken.getId() : null);
        return new ForbiddenException(Error.PASSWORD_LINK_INVALID);
    }
}

package ee.revkassa.service;

import ee.revkassa.controller.admin.organisation.dto.OrganisationDto;
import ee.revkassa.controller.admin.organisation.dto.OrganisationUpdateRequestDto;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.mail.MailMessage;
import ee.revkassa.infrastructure.mail.MailSendFailedException;
import ee.revkassa.infrastructure.mail.Mailer;
import ee.revkassa.infrastructure.validation.Iban;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.organisation.Organisation;
import ee.revkassa.persistence.organisation.OrganisationMapper;
import ee.revkassa.persistence.organisation.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * The payee name and IBAN that members transfer to (B15) and invoices show (B26). An admin sets them. Because a changed
 * IBAN sends members' money elsewhere, every IBAN change is emailed to all active admins, so a change nobody meant to
 * make is noticed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganisationService {

    private static final DateTimeFormatter EMAIL_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String NO_IBAN = "(puudus)";

    private final OrganisationRepository organisationRepository;
    private final OrganisationMapper organisationMapper;
    private final MemberRepository memberRepository;
    private final Mailer mailer;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OrganisationDto getOrganisation() {
        return organisationMapper.toOrganisationDto(getValidOrganisation());
    }

    @Transactional
    public OrganisationDto updateOrganisation(OrganisationUpdateRequestDto request, Long adminId) {
        Organisation organisation = getValidOrganisation();
        validateVersion(organisation, request.getVersion());
        Member admin = getValidMemberBy(adminId);
        String oldIban = organisation.getIban();
        applyChanges(organisation, request, admin);
        // Version conflicts and constraint errors surface here, before anyone is told about the change
        organisationRepository.flush();
        log.info("Organisation details updated: adminId={}, ibanChanged={}", adminId, ibanChanged(oldIban, organisation));
        if (ibanChanged(oldIban, organisation)) {
            notifyAdminsOfIbanChange(oldIban, organisation, admin);
        }
        return organisationMapper.toOrganisationDto(organisation);
    }

    private Organisation getValidOrganisation() {
        return organisationRepository.findById(Organisation.ID)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // JPA checks the version it loaded itself; this compares the version the admin's form was loaded with
    private static void validateVersion(Organisation organisation, Long requestVersion) {
        if (!organisation.getVersion().equals(requestVersion)) {
            throw new ObjectOptimisticLockingFailureException(Organisation.class, organisation.getId());
        }
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private void applyChanges(Organisation organisation, OrganisationUpdateRequestDto request, Member admin) {
        organisation.setName(request.getName().strip());
        organisation.setIban(Iban.normalize(request.getIban()));
        organisation.setUpdatedBy(admin);
        organisation.setUpdatedAt(OffsetDateTime.now(clock));
    }

    private static boolean ibanChanged(String oldIban, Organisation organisation) {
        return !Objects.equals(oldIban, organisation.getIban());
    }

    // The change stays saved even when an email fails: the admin who made it should not be blocked by a mail server
    private void notifyAdminsOfIbanChange(String oldIban, Organisation organisation, Member changedBy) {
        for (Member admin : memberRepository.findActiveAdmins()) {
            try {
                mailer.send(createIbanChangedEmail(admin, oldIban, organisation, changedBy));
            } catch (MailSendFailedException e) {
                log.error("IBAN change email not sent: adminId={}, reason={}", admin.getId(), e.getMessage());
            }
        }
        log.info("IBAN change emailed to admins: changedBy={}", changedBy.getId());
    }

    private static MailMessage createIbanChangedEmail(Member admin, String oldIban, Organisation organisation,
                                                      Member changedBy) {
        String changedAt = organisation.getUpdatedAt().atZoneSameInstant(ClockConfig.APP_ZONE).format(EMAIL_TIME);
        return new MailMessage(admin.getEmail(), "RevKassa: ühingu IBAN muudeti", """
                Tere, %s!

                %s muutis %s RevKassas ühingu IBAN-i, kuhu liikmed raha kannavad.

                Vana IBAN: %s
                Uus IBAN:  %s
                Saaja:     %s

                Kui see muudatus on teile teada, pole vaja midagi teha.
                Kui mitte, logi kohe sisse, pane õige IBAN tagasi ja vaheta adminide paroolid.
                """.formatted(admin.getDisplayName(), changedBy.getDisplayName(), changedAt,
                oldIban == null ? NO_IBAN : Iban.group(oldIban), Iban.group(organisation.getIban()),
                organisation.getName()));
    }
}

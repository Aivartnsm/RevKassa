package ee.revkassa.service;

import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.persistence.membershiplevel.MembershipLevelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * SQL scripts contain no passwords, so a fresh database has nobody who can log in as admin. On startup, if there is
 * no active admin, one is created from env REVKASSA_BOOTSTRAP_ADMIN_EMAIL / _PASSWORD / _NAME. Once an active admin
 * exists this does nothing, so the settings can stay in place. The password is never logged.
 */
@Slf4j
@Service
public class AdminBootstrapService {

    static final int MIN_PASSWORD_LENGTH = 10;
    private static final String DEFAULT_ADMIN_NAME = "Admin";

    private final MemberRepository memberRepository;
    private final MembershipLevelRepository membershipLevelRepository;
    private final PinService pinService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public AdminBootstrapService(MemberRepository memberRepository,
                                 MembershipLevelRepository membershipLevelRepository,
                                 PinService pinService,
                                 PasswordEncoder passwordEncoder,
                                 Clock clock,
                                 @Value("${revkassa.bootstrap.admin-email:}") String adminEmail,
                                 @Value("${revkassa.bootstrap.admin-password:}") String adminPassword,
                                 @Value("${revkassa.bootstrap.admin-name:}") String adminName) {
        this.memberRepository = memberRepository;
        this.membershipLevelRepository = membershipLevelRepository;
        this.pinService = pinService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.adminEmail = adminEmail.strip();
        this.adminPassword = adminPassword;
        this.adminName = adminName.isBlank() ? DEFAULT_ADMIN_NAME : adminName.strip();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createFirstAdminIfMissing() {
        if (memberRepository.existsActiveAdmin()) {
            return;
        }
        if (adminEmail.isEmpty() || adminPassword.isEmpty()) {
            log.warn("No active admin and REVKASSA_BOOTSTRAP_ADMIN_EMAIL / _PASSWORD are not set: nobody can manage the app");
            return;
        }
        validateSettings();
        String pin = pinService.generateUniquePin();
        Member admin = memberRepository.save(createAdmin(pin));
        // The only place a PIN is ever logged: the first admin needs it once to buy at the bar; change it later
        log.warn("First admin created: memberId={}. Log in with the bootstrap email and password. PIN (shown only once): {}",
                admin.getId(), pin);
    }

    // Wrong settings stop the startup: silently skipping would leave a fresh install without any admin
    private void validateSettings() {
        if (!adminEmail.contains("@")) {
            throw new IllegalStateException("REVKASSA_BOOTSTRAP_ADMIN_EMAIL is not an email address");
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("REVKASSA_BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (memberRepository.findByEmail(adminEmail).isPresent()) {
            throw new IllegalStateException("REVKASSA_BOOTSTRAP_ADMIN_EMAIL already belongs to a member who is not an active admin");
        }
    }

    private Member createAdmin(String pin) {
        Member admin = new Member();
        admin.setEmail(adminEmail);
        admin.setDisplayName(adminName);
        admin.setPinHmac(pinService.hash(pin));
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(MemberRoleEnum.ADMIN);
        admin.setBalanceCents(0L);
        admin.setActive(true);
        admin.setFeeExempt(false);
        admin.setLevel(getFirstActiveLevel());
        admin.setCreatedAt(OffsetDateTime.now(clock));
        return admin;
    }

    private MembershipLevel getFirstActiveLevel() {
        return membershipLevelRepository.findAllActive().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active membership level: run docs/database/2_create.sql"));
    }
}

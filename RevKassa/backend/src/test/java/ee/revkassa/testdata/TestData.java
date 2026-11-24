package ee.revkassa.testdata;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.persistence.trusteddevice.TrustedDevice;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

import java.time.OffsetDateTime;

/**
 * Creates the rows a test needs itself, so tests do not depend on docs/database/3_import.sql contents.
 * Use inside a @Transactional test: everything is rolled back afterwards.
 */
@TestComponent
@RequiredArgsConstructor
public class TestData {

    private final EntityManager entityManager;

    public Member member(String email, String pinHmac) {
        Member member = new Member();
        member.setEmail(email);
        member.setDisplayName("Test " + email);
        member.setPinHmac(pinHmac);
        member.setRole(MemberRoleEnum.MEMBER);
        member.setBalanceCents(0L);
        member.setActive(true);
        member.setFeeExempt(false);
        member.setLevel(firstLevel());
        member.setCreatedAt(OffsetDateTime.now());
        return persist(member);
    }

    public TrustedDevice trustedDevice(String name, String tokenHash, Member createdBy) {
        TrustedDevice device = new TrustedDevice();
        device.setName(name);
        device.setTokenHash(tokenHash);
        device.setCreatedBy(createdBy);
        device.setCreatedAt(OffsetDateTime.now());
        return persist(device);
    }

    public void revoke(TrustedDevice device, Member revokedBy) {
        device.setRevokedBy(revokedBy);
        device.setRevokedAt(OffsetDateTime.now());
        entityManager.flush();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }

    private MembershipLevel firstLevel() {
        return entityManager
                .createQuery("select l from MembershipLevel l order by l.sortOrder", MembershipLevel.class)
                .setMaxResults(1)
                .getSingleResult();
    }
}

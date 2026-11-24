package ee.revkassa.service;

import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.passwordtoken.PasswordTokenPurposeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin actions on members. B17 adds creating and editing members here.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordLinkService passwordLinkService;

    @Transactional
    public void sendInvite(Long memberId, Long adminId) {
        Member member = getValidMemberBy(memberId);
        validateActive(member);
        passwordLinkService.send(member, PasswordTokenPurposeEnum.INVITE, getValidMemberBy(adminId));
    }

    // An inactive member could not use the link anyway
    private static void validateActive(Member member) {
        if (!member.getActive()) {
            throw new ForbiddenException(Error.ACCOUNT_NOT_ACTIVE);
        }
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }
}

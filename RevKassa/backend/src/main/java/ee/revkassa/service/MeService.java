package ee.revkassa.service;

import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberMapper;
import ee.revkassa.persistence.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;

    // The member always comes from the session, never from the request, so nobody can read another member's data
    @Transactional(readOnly = true)
    public MemberSessionDto getMe(MemberPrincipal principal) {
        Member member = getValidMemberBy(principal.memberId());
        return memberMapper.toMemberSessionDto(member, principal.loginMethod());
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }
}

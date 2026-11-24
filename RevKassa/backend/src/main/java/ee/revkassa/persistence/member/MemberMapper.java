package ee.revkassa.persistence.member;

import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.infrastructure.security.LoginMethodEnum;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.displayName", target = "displayName")
    @Mapping(source = "member.role", target = "role")
    @Mapping(source = "member.balanceCents", target = "balanceCents")
    @Mapping(source = "loginMethod", target = "loginMethod")
    MemberSessionDto toMemberSessionDto(Member member, LoginMethodEnum loginMethod);
}

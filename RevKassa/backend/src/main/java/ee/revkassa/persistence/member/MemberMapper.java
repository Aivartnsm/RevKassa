package ee.revkassa.persistence.member;

import ee.revkassa.controller.admin.member.dto.AdminMemberDto;
import ee.revkassa.controller.admin.member.dto.MemberCreateRequestDto;
import ee.revkassa.controller.admin.member.dto.MemberUpdateRequestDto;
import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.infrastructure.security.LoginMethodEnum;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.displayName", target = "displayName")
    @Mapping(source = "member.role", target = "role")
    @Mapping(source = "member.balanceCents", target = "balanceCents")
    @Mapping(source = "loginMethod", target = "loginMethod")
    MemberSessionDto toMemberSessionDto(Member member, LoginMethodEnum loginMethod);

    @Mapping(source = "id", target = "memberId")
    @Mapping(source = "displayName", target = "displayName")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "role", target = "role")
    @Mapping(source = "active", target = "active")
    @Mapping(source = "feeExempt", target = "feeExempt")
    @Mapping(source = "balanceCents", target = "balanceCents")
    @Mapping(target = "passwordSet", expression = "java(member.getPasswordHash() != null)")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "version", target = "version")
    AdminMemberDto toAdminMemberDto(Member member);

    List<AdminMemberDto> toAdminMemberDtos(List<Member> members);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "email", target = "email")
    @Mapping(source = "displayName", target = "displayName")
    @Mapping(target = "pinHmac", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(source = "role", target = "role")
    @Mapping(target = "balanceCents", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(source = "feeExempt", target = "feeExempt")
    @Mapping(target = "pinChangedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "level", ignore = true)
    Member toMember(MemberCreateRequestDto request);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "email", target = "email")
    @Mapping(source = "displayName", target = "displayName")
    @Mapping(target = "pinHmac", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(source = "role", target = "role")
    @Mapping(target = "balanceCents", ignore = true)
    @Mapping(source = "active", target = "active")
    @Mapping(source = "feeExempt", target = "feeExempt")
    @Mapping(target = "pinChangedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "level", ignore = true)
    void updateMember(MemberUpdateRequestDto request, @MappingTarget Member member);
}

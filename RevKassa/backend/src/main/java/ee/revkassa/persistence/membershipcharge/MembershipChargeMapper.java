package ee.revkassa.persistence.membershipcharge;

import ee.revkassa.controller.admin.membershipfee.dto.MembershipChargeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MembershipChargeMapper {

    @Mapping(source = "id", target = "chargeId")
    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.displayName", target = "memberName")
    @Mapping(source = "level.name", target = "levelName")
    @Mapping(source = "amountCents", target = "amountCents")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "waivedAt", target = "waivedAt")
    @Mapping(source = "waivedBy.displayName", target = "waivedByName")
    @Mapping(source = "waiveNote", target = "waiveNote")
    MembershipChargeDto toMembershipChargeDto(MembershipCharge charge);

    List<MembershipChargeDto> toMembershipChargeDtos(List<MembershipCharge> charges);
}

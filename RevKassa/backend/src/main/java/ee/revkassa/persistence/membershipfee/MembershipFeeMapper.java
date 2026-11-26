package ee.revkassa.persistence.membershipfee;

import ee.revkassa.controller.admin.membershipfee.dto.MembershipFeeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MembershipFeeMapper {

    @Mapping(target = "validFrom", expression = "java(java.time.YearMonth.from(fee.getValidFrom()))")
    @Mapping(source = "amountCents", target = "amountCents")
    @Mapping(source = "createdBy.displayName", target = "createdByName")
    @Mapping(source = "createdAt", target = "createdAt")
    MembershipFeeDto toMembershipFeeDto(MembershipFee fee);

    List<MembershipFeeDto> toMembershipFeeDtos(List<MembershipFee> fees);
}

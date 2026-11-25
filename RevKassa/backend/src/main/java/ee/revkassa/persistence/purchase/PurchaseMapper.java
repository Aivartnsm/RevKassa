package ee.revkassa.persistence.purchase;

import ee.revkassa.controller.admin.member.dto.MemberPurchaseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PurchaseMapper {

    @Mapping(source = "id", target = "purchaseId")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "totalCents", target = "totalCents")
    @Mapping(target = "lines", ignore = true)
    @Mapping(source = "reversedAt", target = "reversedAt")
    @Mapping(source = "reversedBy.displayName", target = "reversedByName")
    MemberPurchaseDto toMemberPurchaseDto(Purchase purchase);
}

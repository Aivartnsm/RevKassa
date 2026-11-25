package ee.revkassa.persistence.accountentry;

import ee.revkassa.controller.me.dto.AccountEntryDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountEntryMapper {

    @Mapping(source = "id", target = "entryId")
    @Mapping(source = "type", target = "type")
    @Mapping(source = "amountCents", target = "amountCents")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "purchase.id", target = "purchaseId")
    @Mapping(target = "description", ignore = true)
    AccountEntryDto toAccountEntryDto(AccountEntry entry);
}

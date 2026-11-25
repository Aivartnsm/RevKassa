package ee.revkassa.persistence.organisation;

import ee.revkassa.controller.admin.organisation.dto.OrganisationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrganisationMapper {

    @Mapping(source = "name", target = "name")
    @Mapping(source = "iban", target = "iban")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "version", target = "version")
    OrganisationDto toOrganisationDto(Organisation organisation);
}

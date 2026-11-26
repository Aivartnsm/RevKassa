package ee.revkassa.persistence.membershiplevel;

import ee.revkassa.controller.admin.membershiplevel.dto.MembershipLevelDto;
import ee.revkassa.controller.admin.membershiplevel.dto.MembershipLevelUpdateRequestDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface MembershipLevelMapper {

    @Mapping(source = "id", target = "levelId")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "sortOrder", target = "sortOrder")
    @Mapping(source = "active", target = "active")
    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "deletable", ignore = true)
    MembershipLevelDto toMembershipLevelDto(MembershipLevel level);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "name", target = "name")
    @Mapping(source = "sortOrder", target = "sortOrder")
    @Mapping(source = "active", target = "active")
    @Mapping(target = "createdAt", ignore = true)
    void updateMembershipLevel(MembershipLevelUpdateRequestDto request, @MappingTarget MembershipLevel level);
}

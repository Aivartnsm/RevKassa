package ee.revkassa.persistence.eventparticipant;

import ee.revkassa.controller.admin.event.dto.EventParticipantDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventParticipantMapper {

    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.displayName", target = "displayName")
    @Mapping(source = "level.name", target = "levelName")
    @Mapping(source = "feeCents", target = "feeCents")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "charged", target = "charged")
    @Mapping(source = "selfSignup", target = "selfSignup")
    @Mapping(source = "addedBy.displayName", target = "addedByName")
    @Mapping(source = "addedAt", target = "addedAt")
    @Mapping(source = "removedAt", target = "removedAt")
    EventParticipantDto toEventParticipantDto(EventParticipant participant);

    List<EventParticipantDto> toEventParticipantDtos(List<EventParticipant> participants);
}

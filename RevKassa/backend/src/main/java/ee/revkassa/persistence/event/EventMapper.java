package ee.revkassa.persistence.event;

import ee.revkassa.controller.admin.dashboard.dto.UpcomingEventDto;
import ee.revkassa.controller.admin.event.dto.EventCreateRequestDto;
import ee.revkassa.controller.admin.event.dto.EventDto;
import ee.revkassa.controller.admin.event.dto.EventSummaryDto;
import ee.revkassa.controller.admin.event.dto.EventUpdateRequestDto;
import ee.revkassa.controller.event.dto.MemberEventDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(source = "name", target = "name")
    @Mapping(source = "startsAt", target = "startsAt")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "description", target = "description")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "signupOpen", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Event toEvent(EventCreateRequestDto request);

    @Mapping(source = "name", target = "name")
    @Mapping(source = "startsAt", target = "startsAt")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "signupOpen", target = "signupOpen")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEvent(EventUpdateRequestDto request, @MappingTarget Event event);

    @Mapping(source = "id", target = "eventId")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "startsAt", target = "startsAt")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "signupOpen", target = "signupOpen")
    @Mapping(source = "editable", target = "editable")
    @Mapping(source = "createdBy.displayName", target = "createdByName")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "version", target = "version")
    @Mapping(target = "participantCount", ignore = true)
    @Mapping(target = "feeIncomeCents", ignore = true)
    @Mapping(target = "unchargedCount", ignore = true)
    @Mapping(target = "unchargedCents", ignore = true)
    @Mapping(target = "feePeriods", ignore = true)
    @Mapping(target = "participants", ignore = true)
    @Mapping(target = "budget", ignore = true)
    EventDto toEventDto(Event event);

    @Mapping(source = "id", target = "eventId")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "startsAt", target = "startsAt")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "signupOpen", target = "signupOpen")
    @Mapping(target = "participantCount", ignore = true)
    @Mapping(target = "feeIncomeCents", ignore = true)
    @Mapping(target = "plannedResultCents", ignore = true)
    @Mapping(target = "actualResultCents", ignore = true)
    EventSummaryDto toEventSummaryDto(Event event);

    @Mapping(source = "id", target = "eventId")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "startsAt", target = "startsAt")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "status", target = "status")
    @Mapping(target = "participantCount", ignore = true)
    UpcomingEventDto toUpcomingEventDto(Event event);

    @Mapping(source = "id", target = "eventId")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "startsAt", target = "startsAt")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "signupOpen", target = "signupOpen")
    @Mapping(target = "participantCount", ignore = true)
    @Mapping(target = "signedUp", ignore = true)
    @Mapping(target = "charged", ignore = true)
    @Mapping(target = "feeCents", ignore = true)
    @Mapping(target = "nextFee", ignore = true)
    MemberEventDto toMemberEventDto(Event event);
}

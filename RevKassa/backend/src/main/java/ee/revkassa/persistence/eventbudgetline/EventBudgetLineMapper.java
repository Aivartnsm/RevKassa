package ee.revkassa.persistence.eventbudgetline;

import ee.revkassa.controller.admin.event.dto.BudgetLineCreateRequestDto;
import ee.revkassa.controller.admin.event.dto.BudgetLineDto;
import ee.revkassa.controller.admin.event.dto.BudgetLineUpdateRequestDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventBudgetLineMapper {

    @Mapping(source = "category", target = "category")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "plannedCents", target = "plannedCents")
    @Mapping(source = "actualCents", target = "actualCents")
    @Mapping(source = "note", target = "note")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "event", ignore = true)
    @Mapping(target = "sortOrder", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    EventBudgetLine toEventBudgetLine(BudgetLineCreateRequestDto request);

    @Mapping(source = "category", target = "category")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "plannedCents", target = "plannedCents")
    @Mapping(source = "actualCents", target = "actualCents")
    @Mapping(source = "note", target = "note")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "event", ignore = true)
    @Mapping(target = "sortOrder", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEventBudgetLine(BudgetLineUpdateRequestDto request, @MappingTarget EventBudgetLine line);

    @Mapping(source = "id", target = "lineId")
    @Mapping(source = "category", target = "category")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "plannedCents", target = "plannedCents")
    @Mapping(source = "actualCents", target = "actualCents")
    @Mapping(source = "note", target = "note")
    @Mapping(source = "source", target = "source")
    BudgetLineDto toBudgetLineDto(EventBudgetLine line);

    List<BudgetLineDto> toBudgetLineDtos(List<EventBudgetLine> lines);
}

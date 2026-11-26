package ee.revkassa.controller.admin.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventFeePeriodDto implements Serializable {
    // null = from the start
    private LocalDate validFrom;
    // Same levels in every period, in level order
    private List<EventFeeDto> fees;
}

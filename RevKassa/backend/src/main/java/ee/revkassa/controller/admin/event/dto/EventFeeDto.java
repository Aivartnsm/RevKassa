package ee.revkassa.controller.admin.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventFeeDto implements Serializable {
    private Long levelId;
    private String levelName;
    // A deactivated level is listed only when it has a fee above 0 in some period: its members can still take part
    private Boolean levelActive;
    // 0 = free for this level
    private long amountCents;
}

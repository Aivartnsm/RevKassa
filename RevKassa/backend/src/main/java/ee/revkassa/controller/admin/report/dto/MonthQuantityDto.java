package ee.revkassa.controller.admin.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.YearMonth;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthQuantityDto implements Serializable {
    private YearMonth month;
    private long quantity;
}

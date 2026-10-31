package ee.revkassa.infrastructure.error;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ClockAndMoneyFormatDto {

    private OffsetDateTime dateTime;
    private long money;
}

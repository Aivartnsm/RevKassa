package ee.revkassa.controller.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * The member's fee from a later date on ("alates 01.12 25 €").
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NextFeeDto implements Serializable {
    private LocalDate validFrom;
    private long feeCents;
}

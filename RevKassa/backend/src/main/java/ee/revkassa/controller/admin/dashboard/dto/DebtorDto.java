package ee.revkassa.controller.admin.dashboard.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * active = false: a former member who still owes ("endine liige").
 */
@Data
public class DebtorDto implements Serializable {
    private Long memberId;
    private String name;
    private Long owedCents;
    private Boolean active;
}

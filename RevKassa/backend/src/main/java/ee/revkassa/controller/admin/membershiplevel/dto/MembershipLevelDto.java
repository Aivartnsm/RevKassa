package ee.revkassa.controller.admin.membershiplevel.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * A membership level for the admin's level panel. memberCount counts active members only; deletable = nothing
 * (no member, also inactive ones, no fee, charge or event) refers to the level.
 */
@Data
public class MembershipLevelDto implements Serializable {
    private Long levelId;
    private String name;
    private Integer sortOrder;
    private Boolean active;
    private Long memberCount;
    private Boolean deletable;
}

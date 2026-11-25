package ee.revkassa.controller.admin.organisation.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class OrganisationDto implements Serializable {
    // null until an admin sets them
    private String name;
    // Without spaces, in capitals; the UI groups it by four
    private String iban;
    private OffsetDateTime updatedAt;
    private Long version;
}

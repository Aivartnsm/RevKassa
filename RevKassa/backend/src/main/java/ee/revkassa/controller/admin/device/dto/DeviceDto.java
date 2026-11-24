package ee.revkassa.controller.admin.device.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * A trusted device as the admin sees it. Never contains the device token or its hash.
 */
@Data
public class DeviceDto implements Serializable {
    private Long id;
    private String name;
    private OffsetDateTime createdAt;
    private String createdByName;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime revokedAt;
    private String revokedByName;
}

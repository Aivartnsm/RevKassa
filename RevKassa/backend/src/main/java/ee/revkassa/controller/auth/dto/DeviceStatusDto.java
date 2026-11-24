package ee.revkassa.controller.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatusDto implements Serializable {
    private boolean trusted;
    private String name;
}

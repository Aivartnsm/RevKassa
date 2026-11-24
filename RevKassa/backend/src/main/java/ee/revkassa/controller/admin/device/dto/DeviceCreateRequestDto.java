package ee.revkassa.controller.admin.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class DeviceCreateRequestDto implements Serializable {

    @NotBlank
    @Size(max = 100)
    private String name;
}

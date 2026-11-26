package ee.revkassa.controller.admin.dashboard.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class TopProductDto implements Serializable {
    private Long productId;
    private String name;
    // Units sold, undone purchases not counted
    private Long quantity;
}

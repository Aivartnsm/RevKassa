package ee.revkassa.controller.me.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavouritesDto implements Serializable {
    // In the member's order; may include hidden products, which the buying view leaves out
    private List<Long> productIds;
}

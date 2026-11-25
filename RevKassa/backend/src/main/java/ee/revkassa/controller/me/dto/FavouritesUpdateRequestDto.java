package ee.revkassa.controller.me.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.UniqueElements;

import java.io.Serializable;
import java.util.List;

@Data
public class FavouritesUpdateRequestDto implements Serializable {

    public static final int MAX_FAVOURITES = 100;

    // The whole list in the new order; empty clears it
    @NotNull
    @Size(max = FavouritesUpdateRequestDto.MAX_FAVOURITES)
    @UniqueElements
    private List<@NotNull @Positive Long> productIds;
}

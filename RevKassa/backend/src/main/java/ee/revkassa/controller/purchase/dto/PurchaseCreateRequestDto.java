package ee.revkassa.controller.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
public class PurchaseCreateRequestDto implements Serializable {

    // Made by the client once per basket and sent again unchanged on a retry: the purchase is recorded once
    @NotNull
    private UUID requestId;

    // The total the member saw; if prices changed meanwhile the server answers 409 instead of charging more or less
    @NotNull
    @Positive
    private Long expectedTotalCents;

    @NotEmpty
    @Size(max = 50)
    private List<@Valid @NotNull PurchaseLineRequestDto> lines;
}

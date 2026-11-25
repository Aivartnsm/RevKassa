package ee.revkassa.controller.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentCreateRequestDto implements Serializable {

    // A bank transfer description holds at most 140 characters
    public static final int MAX_REFERENCE_LENGTH = 140;

    // No upper limit: a member may pay well ahead
    @NotNull
    @Positive
    private Long amountCents;

    // Optional, e.g. the description used in the transfer; blank = none
    @Size(max = PaymentCreateRequestDto.MAX_REFERENCE_LENGTH)
    private String reference;
}

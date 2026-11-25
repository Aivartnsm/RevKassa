package ee.revkassa.controller.admin.payment.dto;

import ee.revkassa.controller.payment.dto.PaymentCreateRequestDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

// A payment the admin saw on the bank statement (or got in cash) and enters as confirmed at once
@Data
public class AdminPaymentCreateRequestDto implements Serializable {

    @NotNull
    @Positive
    private Long memberId;

    @NotNull
    @Positive
    private Long amountCents;

    @Size(max = PaymentCreateRequestDto.MAX_REFERENCE_LENGTH)
    private String reference;
}

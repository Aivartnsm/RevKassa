package ee.revkassa.controller.admin.payment.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentRejectRequestDto implements Serializable {

    public static final int MAX_NOTE_LENGTH = 500;

    // Optional; the member sees it, e.g. "Laekumist pangaväljavõttel ei leitud"
    @Size(max = PaymentRejectRequestDto.MAX_NOTE_LENGTH)
    private String note;
}

package ee.revkassa.controller.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// What the member copies into their bank's transfer form
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailsDto implements Serializable {
    private String recipient;
    // Without spaces, in capitals; the UI groups it by four for reading
    private String iban;
    // Suggested transfer description, so the admin recognises the transfer on the bank statement
    private String description;
}

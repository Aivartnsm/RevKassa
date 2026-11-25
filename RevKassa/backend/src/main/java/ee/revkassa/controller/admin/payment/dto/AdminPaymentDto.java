package ee.revkassa.controller.admin.payment.dto;

import ee.revkassa.persistence.payment.PaymentMethodEnum;
import ee.revkassa.persistence.payment.PaymentStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class AdminPaymentDto implements Serializable {
    private Long paymentId;
    private Long memberId;
    private String memberName;
    private Long amountCents;
    private PaymentMethodEnum method;
    // What the member typed, or the bank reference the admin entered
    private String reference;
    private PaymentStatusEnum status;
    private OffsetDateTime reportedAt;
    // null while PENDING
    private OffsetDateTime decidedAt;
    private String decidedByName;
    private String decisionNote;
}

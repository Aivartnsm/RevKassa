package ee.revkassa.persistence.payment;

import ee.revkassa.controller.admin.payment.dto.AdminPaymentDto;
import ee.revkassa.controller.payment.dto.PaymentDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(source = "id", target = "paymentId")
    @Mapping(source = "amountCents", target = "amountCents")
    @Mapping(source = "method", target = "method")
    @Mapping(source = "reference", target = "reference")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "reportedAt", target = "reportedAt")
    @Mapping(source = "decidedAt", target = "decidedAt")
    @Mapping(source = "decisionNote", target = "decisionNote")
    PaymentDto toPaymentDto(Payment payment);

    List<PaymentDto> toPaymentDtos(List<Payment> payments);

    @Mapping(source = "id", target = "paymentId")
    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.displayName", target = "memberName")
    @Mapping(source = "amountCents", target = "amountCents")
    @Mapping(source = "method", target = "method")
    @Mapping(source = "reference", target = "reference")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "reportedAt", target = "reportedAt")
    @Mapping(source = "decidedAt", target = "decidedAt")
    @Mapping(source = "decidedBy.displayName", target = "decidedByName")
    @Mapping(source = "decisionNote", target = "decisionNote")
    AdminPaymentDto toAdminPaymentDto(Payment payment);

    List<AdminPaymentDto> toAdminPaymentDtos(List<Payment> payments);
}

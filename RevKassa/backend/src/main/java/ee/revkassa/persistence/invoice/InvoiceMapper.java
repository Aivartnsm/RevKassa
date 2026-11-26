package ee.revkassa.persistence.invoice;

import ee.revkassa.controller.admin.invoice.dto.InvoiceDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {

    @Mapping(source = "id", target = "invoiceId")
    @Mapping(source = "number", target = "number")
    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.displayName", target = "memberName")
    @Mapping(source = "emailTo", target = "emailTo")
    @Mapping(source = "amountCents", target = "amountCents")
    @Mapping(source = "dueDate", target = "dueDate")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "error", target = "error")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "sentAt", target = "sentAt")
    @Mapping(source = "sentBy.displayName", target = "sentByName")
    InvoiceDto toInvoiceDto(Invoice invoice);

    List<InvoiceDto> toInvoiceDtos(List<Invoice> invoices);
}

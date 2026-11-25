package ee.revkassa.persistence.purchaseline;

import ee.revkassa.controller.purchase.dto.PurchaseLineDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PurchaseLineMapper {

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "name")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(source = "unitPriceCents", target = "unitPriceCents")
    @Mapping(target = "lineTotalCents", expression = "java(line.getQuantity() * line.getUnitPriceCents())")
    PurchaseLineDto toPurchaseLineDto(PurchaseLine line);

    List<PurchaseLineDto> toPurchaseLineDtos(List<PurchaseLine> lines);
}

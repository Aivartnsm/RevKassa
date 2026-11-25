package ee.revkassa.persistence.product;

import ee.revkassa.controller.admin.product.dto.AdminProductDto;
import ee.revkassa.controller.admin.product.dto.ProductCreateRequestDto;
import ee.revkassa.controller.admin.product.dto.ProductUpdateRequestDto;
import ee.revkassa.controller.product.dto.ProductDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "style", target = "style")
    @Mapping(source = "volumeMl", target = "volumeMl")
    @Mapping(source = "priceCents", target = "priceCents")
    @Mapping(source = "stockQty", target = "stockQty")
    ProductDto toProductDto(Product product);

    List<ProductDto> toProductDtos(List<Product> products);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "style", target = "style")
    @Mapping(source = "volumeMl", target = "volumeMl")
    @Mapping(source = "priceCents", target = "priceCents")
    @Mapping(source = "stockQty", target = "stockQty")
    @Mapping(source = "lowStockThreshold", target = "lowStockThreshold")
    @Mapping(source = "sortOrder", target = "sortOrder")
    @Mapping(source = "active", target = "active")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "version", target = "version")
    AdminProductDto toAdminProductDto(Product product);

    List<AdminProductDto> toAdminProductDtos(List<Product> products);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "name", target = "name")
    @Mapping(source = "style", target = "style")
    @Mapping(source = "volumeMl", target = "volumeMl")
    @Mapping(source = "priceCents", target = "priceCents")
    @Mapping(target = "stockQty", ignore = true)
    @Mapping(source = "lowStockThreshold", target = "lowStockThreshold")
    @Mapping(source = "sortOrder", target = "sortOrder")
    @Mapping(source = "active", target = "active")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Product toProduct(ProductCreateRequestDto request);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "name", target = "name")
    @Mapping(source = "style", target = "style")
    @Mapping(source = "volumeMl", target = "volumeMl")
    @Mapping(source = "priceCents", target = "priceCents")
    @Mapping(target = "stockQty", ignore = true)
    @Mapping(source = "lowStockThreshold", target = "lowStockThreshold")
    @Mapping(source = "sortOrder", target = "sortOrder")
    @Mapping(source = "active", target = "active")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateProduct(ProductUpdateRequestDto request, @MappingTarget Product product);
}

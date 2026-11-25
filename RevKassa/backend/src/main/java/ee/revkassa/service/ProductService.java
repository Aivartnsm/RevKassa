package ee.revkassa.service;

import ee.revkassa.controller.admin.product.dto.AdminProductDto;
import ee.revkassa.controller.admin.product.dto.ProductCreateRequestDto;
import ee.revkassa.controller.admin.product.dto.ProductUpdateRequestDto;
import ee.revkassa.controller.product.dto.ProductDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.product.ProductMapper;
import ee.revkassa.persistence.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Products and their current prices. Products are never deleted (purchases refer to them): an admin hides one with
 * active = false. Stock is not edited here; it follows the stock ledger.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ProductDto> findActiveProducts() {
        return productMapper.toProductDtos(productRepository.findActiveProducts());
    }

    @Transactional(readOnly = true)
    public List<AdminProductDto> findAllProducts() {
        return productMapper.toAdminProductDtos(productRepository.findAllProducts());
    }

    @Transactional
    public AdminProductDto createProduct(ProductCreateRequestDto request, Long adminId) {
        Product product = productMapper.toProduct(request);
        normalizeTexts(product);
        product.setStockQty(0);
        OffsetDateTime now = OffsetDateTime.now(clock);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productRepository.save(product);
        log.info("Product created: productId={}, priceCents={}, adminId={}", product.getId(), product.getPriceCents(), adminId);
        return productMapper.toAdminProductDto(product);
    }

    @Transactional
    public AdminProductDto updateProduct(Long productId, ProductUpdateRequestDto request, Long adminId) {
        Product product = getValidProductBy(productId);
        validateVersion(product, request.getVersion());
        Long oldPriceCents = product.getPriceCents();
        productMapper.updateProduct(request, product);
        normalizeTexts(product);
        product.setUpdatedAt(OffsetDateTime.now(clock));
        productRepository.flush();
        log.info("Product updated: productId={}, priceCents={}->{}, active={}, adminId={}",
                productId, oldPriceCents, product.getPriceCents(), product.getActive(), adminId);
        return productMapper.toAdminProductDto(product);
    }

    private Product getValidProductBy(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // JPA checks the version it loaded itself; this compares the version the admin's form was loaded with
    private static void validateVersion(Product product, Long requestVersion) {
        if (!product.getVersion().equals(requestVersion)) {
            throw new ObjectOptimisticLockingFailureException(Product.class, product.getId());
        }
    }

    private static void normalizeTexts(Product product) {
        product.setName(product.getName().strip());
        String style = product.getStyle();
        product.setStyle(style == null || style.isBlank() ? null : style.strip());
    }
}

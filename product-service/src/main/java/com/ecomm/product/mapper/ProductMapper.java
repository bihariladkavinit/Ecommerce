package com.ecomm.product.mapper;

import com.ecomm.product.dto.request.ProductRequest;
import com.ecomm.product.dto.response.ProductResponse;
import com.ecomm.product.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    /**
     * Creates a new Product entity from a request.
     * id, active, createdAt, updatedAt are managed by JPA — never set from request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(ProductRequest request);

    /**
     * Updates an existing Product entity in-place.
     * id, active (managed separately for soft-delete), createdAt untouched.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ProductRequest request, @MappingTarget Product product);
}

package com.ecomm.product.repository;

import com.ecomm.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Extends {@link JpaSpecificationExecutor} to support dynamic WHERE clauses
 * built at runtime for the paginated GET /products endpoint
 * (filter by categoryId, full-text search on name/description, active=true).
 */
@Repository
public interface ProductRepository extends
        JpaRepository<Product, UUID>,
        JpaSpecificationExecutor<Product> {

    /** Used by GET /products/{id} — only returns active products publicly. */
    Optional<Product> findByIdAndActiveTrue(UUID id);
}

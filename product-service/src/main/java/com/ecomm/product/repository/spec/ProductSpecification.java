package com.ecomm.product.repository.spec;

import com.ecomm.product.entity.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Factory for {@link Specification} predicates used by
 * {@code GET /products?category=&search=}.
 *
 * <p>All public products are always filtered to {@code active = true}.
 * Admin-facing queries that need inactive products bypass this spec.
 */
public final class ProductSpecification {

    private ProductSpecification() {}

    /**
     * Builds a combined specification from optional filter parameters.
     *
     * @param categoryId optional category UUID filter
     * @param search     optional case-insensitive substring match on name OR description
     * @param activeOnly when true, only active=true products are returned
     */
    public static Specification<Product> build(UUID categoryId, String search, boolean activeOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (activeOnly) {
                predicates.add(cb.isTrue(root.get("active")));
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(nameMatch, descMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

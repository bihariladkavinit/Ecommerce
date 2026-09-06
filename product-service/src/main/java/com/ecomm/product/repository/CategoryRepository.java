package com.ecomm.product.repository;

import com.ecomm.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** All categories ordered by name — used for the GET /categories listing. */
    List<Category> findAllByOrderByNameAsc();

    /** Check existence of a parent before creating a child category. */
    boolean existsById(UUID id);
}

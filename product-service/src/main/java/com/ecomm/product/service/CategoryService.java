package com.ecomm.product.service;

import com.ecomm.product.dto.request.CategoryRequest;
import com.ecomm.product.dto.response.CategoryResponse;
import com.ecomm.product.entity.Category;
import com.ecomm.product.exception.BadRequestException;
import com.ecomm.product.exception.ResourceNotFoundException;
import com.ecomm.product.mapper.CategoryMapper;
import com.ecomm.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper      categoryMapper;

    // ── List ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    // ── Create ────────────────────────────────────────────────────────

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        // Validate parent exists if provided
        if (request.getParentId() != null
                && !categoryRepository.existsById(request.getParentId())) {
            throw new BadRequestException(
                    "Parent category not found: " + request.getParentId());
        }

        Category category = categoryMapper.toEntity(request);
        category = categoryRepository.save(category);

        log.info("Created category id={} name={}", category.getId(), category.getName());
        return categoryMapper.toResponse(category);
    }

    // ── Internal lookup (used by ProductService to validate categoryId) ─

    @Transactional(readOnly = true)
    public void assertExists(java.util.UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", categoryId);
        }
    }
}

package com.ecomm.product.mapper;

import com.ecomm.product.dto.request.CategoryRequest;
import com.ecomm.product.dto.response.CategoryResponse;
import com.ecomm.product.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Category toEntity(CategoryRequest request);
}

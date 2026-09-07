package com.ecomm.inventory.mapper;

import com.ecomm.inventory.dto.response.StockResponse;
import com.ecomm.inventory.entity.StockItem;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between {@link StockItem} and {@link StockResponse}.
 *
 * <p>Field names align 1-to-1, so no explicit {@code @Mapping} annotations
 * are required. MapStruct generates the implementation at compile time.
 */
@Mapper(componentModel = "spring")
public interface StockItemMapper {

    StockResponse toResponse(StockItem stockItem);
}

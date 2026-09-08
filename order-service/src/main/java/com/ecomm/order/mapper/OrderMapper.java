package com.ecomm.order.mapper;

import com.ecomm.order.dto.response.OrderItemResponse;
import com.ecomm.order.dto.response.OrderResponse;
import com.ecomm.order.dto.response.OrderStatusResponse;
import com.ecomm.order.entity.Order;
import com.ecomm.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper — converts {@link Order} entity graphs to response DTOs.
 *
 * <p>Registered as a Spring bean via {@code componentModel = "spring"}
 * (set in the compiler annotation processor argument in pom.xml).
 */
@Mapper
public interface OrderMapper {

    /**
     * Maps an {@link Order} to a full {@link OrderResponse}.
     *
     * <p>{@code status} and {@code sagaState} are stored as enums on the entity
     * but returned as strings in the response so clients don't need to import
     * the enum classes.
     */
    @Mapping(target = "status",    expression = "java(order.getStatus().name())")
    @Mapping(target = "sagaState", expression = "java(order.getSagaState().name())")
    OrderResponse toResponse(Order order);

    /** Maps a list of orders — used for paginated responses. */
    List<OrderResponse> toResponseList(List<Order> orders);

    /** Maps a single {@link OrderItem} to its response DTO. */
    OrderItemResponse toItemResponse(OrderItem item);

    /**
     * Lightweight status-only mapping for {@code GET /orders/{id}/status}.
     */
    @Mapping(target = "status",    expression = "java(order.getStatus().name())")
    @Mapping(target = "sagaState", expression = "java(order.getSagaState().name())")
    OrderStatusResponse toStatusResponse(Order order);
}

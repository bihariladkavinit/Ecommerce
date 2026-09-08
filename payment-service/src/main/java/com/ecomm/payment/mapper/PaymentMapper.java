package com.ecomm.payment.mapper;

import com.ecomm.payment.dto.response.PaymentResponse;
import com.ecomm.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper — Payment entity to PaymentResponse DTO.
 * Registered as a Spring bean via componentModel = "spring" (compiler arg).
 */
@Mapper
public interface PaymentMapper {

    @Mapping(target = "status", expression = "java(payment.getStatus().name())")
    PaymentResponse toResponse(Payment payment);
}

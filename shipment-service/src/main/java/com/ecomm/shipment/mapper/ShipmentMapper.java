package com.ecomm.shipment.mapper;

import com.ecomm.shipment.dto.response.ShipmentResponse;
import com.ecomm.shipment.entity.Shipment;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between {@link Shipment} and {@link ShipmentResponse}.
 *
 * <p>All field names align 1-to-1 between the entity and response DTO, so no
 * explicit {@code @Mapping} annotations are required. MapStruct generates the
 * implementation at compile time via the {@code spring} component model so it
 * is injectable as a Spring bean.
 */
@Mapper(componentModel = "spring")
public interface ShipmentMapper {

    ShipmentResponse toResponse(Shipment shipment);
}

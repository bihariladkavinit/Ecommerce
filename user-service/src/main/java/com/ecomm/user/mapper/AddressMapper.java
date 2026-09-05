package com.ecomm.user.mapper;

import com.ecomm.user.dto.request.AddressRequest;
import com.ecomm.user.dto.response.AddressResponse;
import com.ecomm.user.entity.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    /**
     * Maps an Address entity to an AddressResponse DTO.
     * The field name difference: entity has isDefault (boolean field with Lombok),
     * response also has isDefault — MapStruct resolves both via getter names.
     */
    AddressResponse toResponse(Address address);

    /**
     * Creates a new Address entity from an AddressRequest.
     * userId and id are NOT set here — callers must set userId before persisting.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Address toEntity(AddressRequest request);

    /**
     * Updates an existing Address entity in-place from an AddressRequest.
     * id, userId, and createdAt are never touched.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(AddressRequest request, @MappingTarget Address address);
}

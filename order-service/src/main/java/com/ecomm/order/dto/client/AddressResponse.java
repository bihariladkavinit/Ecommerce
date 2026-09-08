package com.ecomm.order.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO received from user-service address lookup.
 * Maps to {@code GET /api/v1/users/{userId}/addresses/{addressId}}.
 *
 * <p>{@code @JsonIgnoreProperties} ensures unknown fields from user-service
 * don't break deserialization as that service evolves.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddressResponse {

    private UUID   id;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String zip;
    private String country;
    private boolean isDefault;
}

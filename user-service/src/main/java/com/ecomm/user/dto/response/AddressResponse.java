package com.ecomm.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AddressResponse {

    private UUID id;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String zip;
    private String country;
    private boolean isDefault;
}

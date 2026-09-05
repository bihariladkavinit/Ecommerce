package com.ecomm.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequest {

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255)
    private String line1;

    @Size(max = 255)
    private String line2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @NotBlank(message = "Zip code is required")
    @Size(max = 20)
    private String zip;

    @NotBlank(message = "Country is required")
    @Size(max = 100)
    private String country;

    private boolean isDefault = false;
}

package com.ecomm.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class UserResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private boolean active;
    private Set<String> roles;   // e.g. ["ROLE_USER"]
    private OffsetDateTime createdAt;
}

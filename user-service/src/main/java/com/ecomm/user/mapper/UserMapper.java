package com.ecomm.user.mapper;

import com.ecomm.user.dto.response.UserResponse;
import com.ecomm.user.entity.Role;
import com.ecomm.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Maps a User entity to a UserResponse DTO.
     * The roles Set<Role> is converted to Set<String> via the named helper below.
     */
    @Mapping(source = "active", target = "active")
    @Mapping(source = "roles", target = "roles", qualifiedByName = "rolesToStrings")
    UserResponse toResponse(User user);

    @Named("rolesToStrings")
    default Set<String> rolesToStrings(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
}

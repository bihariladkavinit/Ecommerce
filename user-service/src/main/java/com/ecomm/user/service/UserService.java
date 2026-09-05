package com.ecomm.user.service;

import com.ecomm.user.dto.request.AddressRequest;
import com.ecomm.user.dto.request.UpdateUserRequest;
import com.ecomm.user.dto.response.AddressResponse;
import com.ecomm.user.dto.response.UserResponse;
import com.ecomm.user.entity.Address;
import com.ecomm.user.entity.User;
import com.ecomm.user.exception.ResourceNotFoundException;
import com.ecomm.user.mapper.AddressMapper;
import com.ecomm.user.mapper.UserMapper;
import com.ecomm.user.repository.AddressRepository;
import com.ecomm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * User profile and address management.
 *
 * <p>Redis cache-aside pattern for user profiles:
 * <ul>
 *   <li>Key: {@code user:{userId}} — stores JSON-serialised {@link UserResponse}</li>
 *   <li>TTL: 15 minutes (matches access-token lifetime)</li>
 *   <li>Eviction: explicit on every update</li>
 * </ul>
 *
 * <p>We store the serialised DTO (not the entity) in Redis so the cache is
 * decoupled from Hibernate and safe to read without a DB connection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String USER_CACHE_PREFIX = "user:";
    private static final long   USER_CACHE_TTL_MIN = 15L;

    private final UserRepository    userRepository;
    private final AddressRepository addressRepository;
    private final UserMapper        userMapper;
    private final AddressMapper     addressMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ── Profile ───────────────────────────────────────────────────────

    /**
     * Returns the profile for the authenticated user.
     * Served from Redis cache when available; falls back to DB on cache miss.
     */
    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        String cacheKey = USER_CACHE_PREFIX + userId;

        // Cache hit
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, UserResponse.class);
            } catch (Exception ex) {
                log.warn("Cache deserialisation failed for key={}, falling back to DB", cacheKey);
            }
        }

        // Cache miss — load from DB
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserResponse response = userMapper.toResponse(user);
        cacheUser(cacheKey, response);
        return response;
    }

    /**
     * Updates firstName, lastName, and/or phone for the authenticated user.
     * Only non-null / non-blank fields in the request are applied.
     * Cache is evicted on any change.
     */
    @Transactional
    public UserResponse updateMe(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName().strip());
        }
        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName().strip());
        }
        if (request.getPhone() != null) {
            // allow clearing phone by passing empty string
            user.setPhone(request.getPhone().isBlank() ? null : request.getPhone().strip());
        }

        user = userRepository.save(user);
        evictUserCache(userId);

        log.debug("Updated profile for userId={}", userId);
        return userMapper.toResponse(user);
    }

    // ── Addresses ─────────────────────────────────────────────────────

    /**
     * Returns all addresses for the authenticated user, default first.
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(UUID userId) {
        return addressRepository
                .findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId)
                .stream()
                .map(addressMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Adds a new address.
     * If {@code isDefault=true} all existing defaults are cleared first to
     * enforce the single-default invariant.
     */
    @Transactional
    public AddressResponse addAddress(UUID userId, AddressRequest request) {
        // Ensure the user exists before creating an address for them
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        if (request.isDefault()) {
            addressRepository.clearDefaultForUser(userId);
        }

        Address address = addressMapper.toEntity(request);
        address.setUserId(userId);

        address = addressRepository.save(address);
        log.debug("Added address id={} for userId={}", address.getId(), userId);
        return addressMapper.toResponse(address);
    }

    /**
     * Updates an existing address owned by the authenticated user.
     *
     * @throws ResourceNotFoundException if the address doesn't exist or belongs to another user
     */
    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));

        if (request.isDefault()) {
            addressRepository.clearDefaultForUser(userId);
        }

        addressMapper.updateEntity(request, address);
        address = addressRepository.save(address);

        log.debug("Updated address id={} for userId={}", addressId, userId);
        return addressMapper.toResponse(address);
    }

    /**
     * Deletes an address owned by the authenticated user.
     *
     * @throws ResourceNotFoundException if the address doesn't exist or belongs to another user
     */
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));

        addressRepository.delete(address);
        log.debug("Deleted address id={} for userId={}", addressId, userId);
    }

    // ── Internal / Feign ──────────────────────────────────────────────

    /**
     * Looks up any user by ID — used by Order Service via internal Feign.
     * Not cached separately; hits the same cache key as {@link #getMe}.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        String cacheKey = USER_CACHE_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, UserResponse.class);
            } catch (Exception ex) {
                log.warn("Cache deserialisation failed for key={}", cacheKey);
            }
        }

        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserResponse response = userMapper.toResponse(user);
        cacheUser(cacheKey, response);
        return response;
    }

    // ── Cache helpers ─────────────────────────────────────────────────

    private void cacheUser(String cacheKey, UserResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, USER_CACHE_TTL_MIN, TimeUnit.MINUTES);
        } catch (Exception ex) {
            // Cache write failure must never break the API response
            log.warn("Failed to cache user profile for key={}: {}", cacheKey, ex.getMessage());
        }
    }

    private void evictUserCache(UUID userId) {
        redisTemplate.delete(USER_CACHE_PREFIX + userId);
    }
}

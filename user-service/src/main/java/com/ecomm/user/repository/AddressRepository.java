package com.ecomm.user.repository;

import com.ecomm.user.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    /**
     * Returns all addresses for a user, default address first.
     */
    List<Address> findByUserIdOrderByIsDefaultDescCreatedAtAsc(UUID userId);

    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Clears the isDefault flag for all addresses belonging to a user.
     * Called before setting a new default to enforce the single-default invariant.
     */
    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId")
    void clearDefaultForUser(UUID userId);
}

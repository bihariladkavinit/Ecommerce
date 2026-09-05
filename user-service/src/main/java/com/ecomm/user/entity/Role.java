package com.ecomm.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * e.g. ROLE_USER, ROLE_ADMIN — must match the seed data in db-init.sql
     */
    @Column(unique = true, nullable = false, length = 50)
    private String name;
}

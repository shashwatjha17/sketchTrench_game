package com.sketchtrench.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A role grants a set of permissions. Stored as plain strings ('PLAYER', 'ADMIN');
 * Spring Security expects authorities prefixed with {@code ROLE_}, so we prefix at
 * assembly time rather than polluting the DB with the framework's convention.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String name;

    public Role(String name) {
        this.name = name;
    }
}

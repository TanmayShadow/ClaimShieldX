package com.example.ClaimShieldX.auth.model;

import com.example.ClaimShieldX.organization.Organization;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name="users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="organization_id", nullable = false)
    private Organization organization;
}

package com.example.ClaimShieldX.auth.model;

import com.example.ClaimShieldX.organization.Organization;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class UserPrincipal implements UserDetails {
    private final UUID id;
    private final String email;
    private final String password;
    private final Organization organization;

    public UserPrincipal(UUID id, String email, String password, Organization organization){
        this.id = id;
        this.email = email;
        this.password = password;
        this.organization = organization;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
}

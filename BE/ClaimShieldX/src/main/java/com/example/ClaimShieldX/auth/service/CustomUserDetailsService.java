package com.example.ClaimShieldX.auth.service;

import com.example.ClaimShieldX.auth.dto.RegisterUserRequest;
import com.example.ClaimShieldX.auth.model.User;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.auth.repository.UserRepository;
import com.example.ClaimShieldX.exceptions.EmailAlreadyExistException;
import com.example.ClaimShieldX.exceptions.OrganizationNotFoundException;
import com.example.ClaimShieldX.organization.Organization;
import com.example.ClaimShieldX.organization.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service

public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;

    public CustomUserDetailsService(UserRepository userRepository, OrganizationService organizationService, PasswordEncoder passwordEncoder){
        this.userRepository = userRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return  new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getOrganization(),
                user.getRole()
        );
    }

    public User getUserFromId(UUID id){
        return userRepository.findWithOrganizationById(id).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public String registerUser(@Valid RegisterUserRequest registerUserRequest) {
        User user = userRepository.findByEmail(registerUserRequest.email()).orElse(null);
        if (user != null)
            throw new EmailAlreadyExistException("Email id is already registered");
        Organization organization = organizationService.findByName(registerUserRequest.organizationName());
        if(organization == null)
                throw new OrganizationNotFoundException("Organization not found");
        user = new User();
        user.setEmail(registerUserRequest.email());
        user.setOrganization(organization);
        user.setPassword(passwordEncoder.encode(registerUserRequest.password()));

        return userRepository.save(user).getEmail();
    }
}

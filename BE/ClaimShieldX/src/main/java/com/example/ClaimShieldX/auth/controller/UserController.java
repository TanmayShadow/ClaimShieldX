package com.example.ClaimShieldX.auth.controller;

import com.example.ClaimShieldX.auth.dto.RegisterUserRequest;
import com.example.ClaimShieldX.auth.service.CustomUserDetailsService;
import com.google.api.client.http.HttpStatusCodes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final CustomUserDetailsService customUserDetailsService;
    public UserController(CustomUserDetailsService customUserDetailsService){
        this.customUserDetailsService = customUserDetailsService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterUserRequest registerUserRequest){
        return new ResponseEntity<>(customUserDetailsService.registerUser(registerUserRequest), HttpStatus.CREATED);
    }

}

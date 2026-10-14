package com.example.ClaimShieldX.claims.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimsController {

    @GetMapping
    public ResponseEntity<?> getClaims(){
        return new ResponseEntity<>("Working", HttpStatus.OK);
    }
}

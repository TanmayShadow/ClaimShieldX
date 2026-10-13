package com.example.ClaimShieldX.organization;

import com.example.ClaimShieldX.organization.dto.OrganizationCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.print.attribute.standard.OrientationRequested;

@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {
    private OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService){
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<?> createOrganization(@Valid @RequestBody OrganizationCreateRequest organizationCreateRequest){
        return new ResponseEntity<Organization>(this.organizationService.createOrganization(organizationCreateRequest),HttpStatus.CREATED);
    }
}

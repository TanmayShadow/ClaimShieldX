package com.example.ClaimShieldX.organization;

import com.example.ClaimShieldX.exceptions.OrganizationNameAlreadyExistException;
import com.example.ClaimShieldX.organization.dto.OrganizationCreateRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;

@Service
public class OrganizationService {
    private OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository){
        this.organizationRepository = organizationRepository;
    }

    public Organization toEntity(OrganizationCreateRequest organizationCreateRequest){
        Organization organization = new Organization();
        organization.setName(organizationCreateRequest.name());
        return  organization;
    }

    public Organization createOrganization(OrganizationCreateRequest request){
        Organization organization = this.organizationRepository.findByName(request.name());
        if(organization!=null)
            throw new OrganizationNameAlreadyExistException(request.name());
        organization = toEntity(request);
        return this.organizationRepository.save(organization);
    }

    public Organization findByName(@NotBlank(message = "Org. name cannot be blank") String orgName) {
        return  organizationRepository.findByName(orgName);
    }
}

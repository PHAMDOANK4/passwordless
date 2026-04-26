package org.openidentityplatform.passwordless.iam.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.openidentityplatform.passwordless.iam.dto.CreateDomainRequest;
import org.openidentityplatform.passwordless.iam.dto.DomainResponse;
import org.openidentityplatform.passwordless.iam.dto.UpdateDomainRequest;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DomainAdminService {

    private final DomainRepository domainRepository;
    private final UserRepository userRepository;

    public List<DomainResponse> listDomains() {
        return domainRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public DomainResponse getDomain(String id) {
        return domainRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("Domain not found"));
    }

    @Transactional
    public DomainResponse createDomain(CreateDomainRequest request) {
        if (domainRepository.existsByDomainName(request.getDomainName())) {
            throw new IllegalArgumentException("Domain already exists");
        }

        Domain domain = new Domain();
        domain.setDomainName(request.getDomainName().trim().toLowerCase());
        domain.setDisplayName(request.getDisplayName().trim());
        domain.setDescription(request.getDescription());
        domain.setOwnerEmail(request.getOwnerEmail().trim().toLowerCase());
        domain.setActive(!Boolean.FALSE.equals(request.getActive()));
        domain.setRequireMfa(Boolean.TRUE.equals(request.getRequireMfa()));
        domain.setSsoEnabled(Boolean.TRUE.equals(request.getSsoEnabled()));
        domain.setSsoConfig(request.getSsoConfig());
        domain.setCustomLoginUrl(request.getCustomLoginUrl());
        domain.setMaxUsers(request.getMaxUsers());
        domain.setLogoUrl(request.getLogoUrl());

        return toResponse(domainRepository.save(domain));
    }

    @Transactional
    public DomainResponse updateDomain(String id, UpdateDomainRequest request) {
        Domain domain = domainRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Domain not found"));

        if (request.getDomainName() != null && !request.getDomainName().isBlank()) {
            String normalizedDomainName = request.getDomainName().trim().toLowerCase();
            if (!normalizedDomainName.equals(domain.getDomainName()) && domainRepository.existsByDomainName(normalizedDomainName)) {
                throw new IllegalArgumentException("Domain already exists");
            }
            domain.setDomainName(normalizedDomainName);
        }
        if (request.getDisplayName() != null) {
            domain.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getDescription() != null) {
            domain.setDescription(request.getDescription());
        }
        if (request.getOwnerEmail() != null) {
            domain.setOwnerEmail(request.getOwnerEmail().trim().toLowerCase());
        }
        if (request.getActive() != null) {
            domain.setActive(request.getActive());
        }
        if (request.getRequireMfa() != null) {
            domain.setRequireMfa(request.getRequireMfa());
        }
        if (request.getSsoEnabled() != null) {
            domain.setSsoEnabled(request.getSsoEnabled());
        }
        if (request.getSsoConfig() != null) {
            domain.setSsoConfig(request.getSsoConfig());
        }
        if (request.getMaxUsers() != null) {
            domain.setMaxUsers(request.getMaxUsers());
        }
        if (request.getLogoUrl() != null) {
            domain.setLogoUrl(request.getLogoUrl());
        }
        if (request.getCustomLoginUrl() != null) {
            domain.setCustomLoginUrl(request.getCustomLoginUrl());
        }

        return toResponse(domainRepository.save(domain));
    }

    @Transactional
    public void deleteDomain(String id) {
        Domain domain = domainRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Domain not found"));

        if (userRepository.countByDomainId(domain.getId()) > 0) {
            throw new IllegalArgumentException("Domain still has users assigned");
        }

        domainRepository.delete(domain);
    }

    private DomainResponse toResponse(Domain domain) {
        DomainResponse response = new DomainResponse();
        response.setId(domain.getId());
        response.setDomainName(domain.getDomainName());
        response.setDisplayName(domain.getDisplayName());
        response.setDescription(domain.getDescription());
        response.setOwnerEmail(domain.getOwnerEmail());
        response.setActive(domain.isActive());
        response.setRequireMfa(domain.isRequireMfa());
        response.setSsoEnabled(domain.isSsoEnabled());
        response.setSsoConfig(domain.getSsoConfig());
        response.setMaxUsers(domain.getMaxUsers());
        response.setCurrentUsers(domain.getId() != null ? Math.toIntExact(userRepository.countByDomainId(domain.getId())) : 0);
        response.setCustomLoginUrl(domain.getCustomLoginUrl());
        response.setLogoUrl(domain.getLogoUrl());
        response.setCreatedAt(domain.getCreatedAt());
        response.setUpdatedAt(domain.getUpdatedAt());
        return response;
    }
}
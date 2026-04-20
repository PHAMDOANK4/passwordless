package org.openidentityplatform.passwordless.iam.services;

import lombok.RequiredArgsConstructor;
import org.openidentityplatform.passwordless.exceptions.NotFoundException;
import org.openidentityplatform.passwordless.iam.dto.CreateDomainRequest;
import org.openidentityplatform.passwordless.iam.dto.DomainResponse;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class DomainService {

    private final DomainRepository domainRepository;
    private final UserRepository userRepository;

    @Transactional
    public DomainResponse createDomain(CreateDomainRequest request) {
        if (domainRepository.existsByDomainName(request.getDomainName())) {
            throw new IllegalArgumentException("Domain name already exists: " + request.getDomainName());
        }

        Domain domain = new Domain();
        domain.setDomainName(request.getDomainName());
        domain.setDisplayName(request.getDisplayName());
        domain.setDescription(request.getDescription());
        domain.setOwnerEmail(request.getOwnerEmail());
        if (request.getRequireMfa() != null) {
            domain.setRequireMfa(request.getRequireMfa());
        }
        domain.setMaxUsers(request.getMaxUsers());
        domain.setLogoUrl(request.getLogoUrl());

        domain = domainRepository.save(domain);
        return toDomainResponse(domain);
    }

    @Transactional(readOnly = true)
    public DomainResponse getDomain(String id) throws NotFoundException {
        Domain domain = domainRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Domain not found: " + id));
        return toDomainResponse(domain);
    }

    @Transactional(readOnly = true)
    public DomainResponse getDomainByName(String domainName) throws NotFoundException {
        Domain domain = domainRepository.findByDomainName(domainName)
                .orElseThrow(() -> new NotFoundException("Domain not found: " + domainName));
        return toDomainResponse(domain);
    }

    @Transactional(readOnly = true)
    public List<DomainResponse> listDomains() {
        return StreamSupport.stream(domainRepository.findByActiveTrue().spliterator(), false)
                .map(this::toDomainResponse)
                .toList();
    }

    @Transactional
    public DomainResponse deactivateDomain(String id) throws NotFoundException {
        Domain domain = domainRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Domain not found: " + id));
        domain.setActive(false);
        domain = domainRepository.save(domain);
        return toDomainResponse(domain);
    }

    private DomainResponse toDomainResponse(Domain domain) {
        DomainResponse response = new DomainResponse();
        response.setId(domain.getId());
        response.setDomainName(domain.getDomainName());
        response.setDisplayName(domain.getDisplayName());
        response.setDescription(domain.getDescription());
        response.setOwnerEmail(domain.getOwnerEmail());
        response.setActive(domain.isActive());
        response.setRequireMfa(domain.isRequireMfa());
        response.setSsoEnabled(domain.isSsoEnabled());
        response.setMaxUsers(domain.getMaxUsers());
        response.setCurrentUsers((int) userRepository.countByDomainId(domain.getId()));
        response.setLogoUrl(domain.getLogoUrl());
        response.setCreatedAt(domain.getCreatedAt());
        response.setUpdatedAt(domain.getUpdatedAt());
        return response;
    }
}

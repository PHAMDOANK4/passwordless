package org.openidentityplatform.passwordless.iam.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.openidentityplatform.passwordless.exceptions.NotFoundException;
import org.openidentityplatform.passwordless.iam.dto.CreateDomainRequest;
import org.openidentityplatform.passwordless.iam.dto.DomainResponse;
import org.openidentityplatform.passwordless.iam.services.DomainService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DomainResponse createDomain(@Valid @RequestBody CreateDomainRequest request) {
        return domainService.createDomain(request);
    }

    @GetMapping("/{id}")
    public DomainResponse getDomain(@PathVariable String id) throws NotFoundException {
        return domainService.getDomain(id);
    }

    @GetMapping
    public List<DomainResponse> listDomains() {
        return domainService.listDomains();
    }

    @PostMapping("/{id}/deactivate")
    public DomainResponse deactivateDomain(@PathVariable String id) throws NotFoundException {
        return domainService.deactivateDomain(id);
    }
}

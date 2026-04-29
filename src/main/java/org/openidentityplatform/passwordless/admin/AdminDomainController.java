package org.openidentityplatform.passwordless.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.openidentityplatform.passwordless.iam.dto.CreateDomainRequest;
import org.openidentityplatform.passwordless.iam.dto.DomainResponse;
import org.openidentityplatform.passwordless.iam.dto.UpdateDomainRequest;
import org.openidentityplatform.passwordless.iam.services.DomainAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/admin/api/domains")
@RequiredArgsConstructor
public class AdminDomainController {

    private final DomainAdminService domainAdminService;

    @GetMapping
    public ResponseEntity<List<DomainResponse>> listDomains() {
        return ResponseEntity.ok(domainAdminService.listDomains());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDomain(@PathVariable String id) {
        try {
            return ResponseEntity.ok(domainAdminService.getDomain(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Domain not found"));
        }
    }

    @PostMapping
    public ResponseEntity<?> createDomain(@RequestBody @Valid CreateDomainRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(domainAdminService.createDomain(request));
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().contains("already exists")
                    ? HttpStatus.CONFLICT
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDomain(@PathVariable String id, @RequestBody @Valid UpdateDomainRequest request) {
        try {
            return ResponseEntity.ok(domainAdminService.updateDomain(id, request));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Domain not found"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().contains("already exists")
                    ? HttpStatus.CONFLICT
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteDomain(@PathVariable String id) {
        try {
            domainAdminService.deleteDomain(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Domain not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
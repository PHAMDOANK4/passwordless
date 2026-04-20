package org.openidentityplatform.passwordless.iam.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openidentityplatform.passwordless.exceptions.NotFoundException;
import org.openidentityplatform.passwordless.iam.dto.CreateDomainRequest;
import org.openidentityplatform.passwordless.iam.dto.DomainResponse;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DomainServiceTest {

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DomainService domainService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateDomain_success() throws NotFoundException {
        // Given
        CreateDomainRequest request = new CreateDomainRequest();
        request.setDomainName("test.com");
        request.setDisplayName("Test Org");
        request.setDescription("A test domain");
        request.setOwnerEmail("admin@test.com");

        when(domainRepository.existsByDomainName("test.com")).thenReturn(false);

        Domain savedDomain = new Domain();
        savedDomain.setId("domain-1");
        savedDomain.setDomainName("test.com");
        savedDomain.setDisplayName("Test Org");
        savedDomain.setDescription("A test domain");
        savedDomain.setOwnerEmail("admin@test.com");
        savedDomain.setActive(true);

        when(domainRepository.save(any(Domain.class))).thenReturn(savedDomain);
        when(userRepository.countByDomainId("domain-1")).thenReturn(0L);

        // When
        DomainResponse response = domainService.createDomain(request);

        // Then
        assertNotNull(response);
        assertEquals("domain-1", response.getId());
        assertEquals("test.com", response.getDomainName());
        assertEquals("Test Org", response.getDisplayName());
        assertTrue(response.isActive());
        verify(domainRepository).save(any(Domain.class));
    }

    @Test
    void testCreateDomain_duplicateName() {
        // Given
        CreateDomainRequest request = new CreateDomainRequest();
        request.setDomainName("existing.com");
        request.setDisplayName("Existing");
        request.setOwnerEmail("admin@existing.com");

        when(domainRepository.existsByDomainName("existing.com")).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> domainService.createDomain(request));
        verify(domainRepository, never()).save(any(Domain.class));
    }

    @Test
    void testGetDomain_found() throws NotFoundException {
        // Given
        Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setDomainName("test.com");
        domain.setDisplayName("Test Org");
        domain.setOwnerEmail("admin@test.com");
        domain.setActive(true);

        when(domainRepository.findById("domain-1")).thenReturn(Optional.of(domain));
        when(userRepository.countByDomainId("domain-1")).thenReturn(5L);

        // When
        DomainResponse response = domainService.getDomain("domain-1");

        // Then
        assertNotNull(response);
        assertEquals("domain-1", response.getId());
        assertEquals("test.com", response.getDomainName());
        assertEquals(5, response.getCurrentUsers());
    }

    @Test
    void testGetDomain_notFound() {
        // Given
        when(domainRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> domainService.getDomain("nonexistent"));
    }
}

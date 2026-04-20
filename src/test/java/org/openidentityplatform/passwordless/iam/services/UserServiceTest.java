package org.openidentityplatform.passwordless.iam.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openidentityplatform.passwordless.exceptions.NotFoundException;
import org.openidentityplatform.passwordless.iam.dto.CreateUserRequest;
import org.openidentityplatform.passwordless.iam.dto.UserResponse;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DomainRepository domainRepository;

    @InjectMocks
    private UserService userService;

    private Domain domain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        domain = new Domain();
        domain.setId("domain-1");
        domain.setDomainName("test.com");
        domain.setDisplayName("Test Org");
        domain.setActive(true);
    }

    @Test
    void testCreateUser_success() throws NotFoundException {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("user@test.com");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setRole("USER");

        when(domainRepository.findById("domain-1")).thenReturn(Optional.of(domain));
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(userRepository.countByDomainId("domain-1")).thenReturn(0L);

        User savedUser = new User();
        savedUser.setId("user-1");
        savedUser.setEmail("user@test.com");
        savedUser.setFirstName("John");
        savedUser.setLastName("Doe");
        savedUser.setDomain(domain);
        savedUser.setRole(User.UserRole.USER);
        savedUser.setStatus(User.UserStatus.ACTIVE);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When
        UserResponse response = userService.createUser("domain-1", request);

        // Then
        assertNotNull(response);
        assertEquals("user-1", response.getId());
        assertEquals("user@test.com", response.getEmail());
        assertEquals("domain-1", response.getDomainId());
        assertEquals("John", response.getFirstName());
        assertEquals("Doe", response.getLastName());
        assertEquals("USER", response.getRole());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testCreateUser_duplicateEmail() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("existing@test.com");
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setRole("USER");

        when(domainRepository.findById("domain-1")).thenReturn(Optional.of(domain));
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> userService.createUser("domain-1", request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testCreateUser_domainNotFound() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("user@test.com");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setRole("USER");

        when(domainRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> userService.createUser("nonexistent", request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testGetUser_found() throws NotFoundException {
        // Given
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@test.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setDomain(domain);
        user.setRole(User.UserRole.USER);
        user.setStatus(User.UserStatus.ACTIVE);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        // When
        UserResponse response = userService.getUser("domain-1", "user-1");

        // Then
        assertNotNull(response);
        assertEquals("user-1", response.getId());
        assertEquals("user@test.com", response.getEmail());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void testDeactivateUser() throws NotFoundException {
        // Given
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@test.com");
        user.setDomain(domain);
        user.setRole(User.UserRole.USER);
        user.setStatus(User.UserStatus.ACTIVE);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        User suspendedUser = new User();
        suspendedUser.setId("user-1");
        suspendedUser.setEmail("user@test.com");
        suspendedUser.setDomain(domain);
        suspendedUser.setRole(User.UserRole.USER);
        suspendedUser.setStatus(User.UserStatus.SUSPENDED);

        when(userRepository.save(any(User.class))).thenReturn(suspendedUser);

        // When
        UserResponse response = userService.deactivateUser("domain-1", "user-1");

        // Then
        assertNotNull(response);
        assertEquals("SUSPENDED", response.getStatus());
        verify(userRepository).save(any(User.class));
    }
}

package org.openidentityplatform.passwordless.iam.services;

import lombok.RequiredArgsConstructor;
import org.openidentityplatform.passwordless.exceptions.NotFoundException;
import org.openidentityplatform.passwordless.iam.dto.CreateUserRequest;
import org.openidentityplatform.passwordless.iam.dto.UserResponse;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DomainRepository domainRepository;

    @Transactional
    public UserResponse createUser(String domainId, CreateUserRequest request) throws NotFoundException {
        Domain domain = domainRepository.findById(domainId)
                .orElseThrow(() -> new NotFoundException("Domain not found: " + domainId));

        if (!domain.isActive()) {
            throw new IllegalArgumentException("Domain is not active: " + domainId);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        if (domain.getMaxUsers() != null) {
            long currentCount = userRepository.countByDomainId(domainId);
            if (currentCount >= domain.getMaxUsers()) {
                throw new IllegalArgumentException("Domain user quota exceeded. Max users: " + domain.getMaxUsers());
            }
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setDomain(domain);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(User.UserRole.valueOf(request.getRole()));
        if (request.getMfaEnabled() != null) {
            user.setMfaEnabled(request.getMfaEnabled());
        }
        if (request.getPreferredMfaMethod() != null) {
            user.setPreferredMfaMethod(User.MfaMethod.valueOf(request.getPreferredMfaMethod()));
        }

        user = userRepository.save(user);
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(String domainId, String id) throws NotFoundException {
        User user = findUserInDomain(domainId, id);
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) throws NotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsersByDomain(String domainId, Pageable pageable) {
        return userRepository.findByDomainId(domainId, pageable)
                .map(this::toUserResponse);
    }

    @Transactional
    public UserResponse deactivateUser(String domainId, String id) throws NotFoundException {
        User user = findUserInDomain(domainId, id);
        user.setStatus(User.UserStatus.SUSPENDED);
        user = userRepository.save(user);
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse activateUser(String domainId, String id) throws NotFoundException {
        User user = findUserInDomain(domainId, id);
        user.setStatus(User.UserStatus.ACTIVE);
        user = userRepository.save(user);
        return toUserResponse(user);
    }

    private User findUserInDomain(String domainId, String userId) throws NotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        if (!user.getDomain().getId().equals(domainId)) {
            throw new NotFoundException("User not found in domain: " + domainId);
        }
        return user;
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setDomainId(user.getDomain().getId());
        response.setDomainName(user.getDomain().getDomainName());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setDisplayName(user.getDisplayName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setStatus(user.getStatus().name());
        response.setMfaEnabled(user.isMfaEnabled());
        response.setPreferredMfaMethod(
                user.getPreferredMfaMethod() != null ? user.getPreferredMfaMethod().name() : null);
        response.setRole(user.getRole().name());
        response.setProfilePictureUrl(user.getProfilePictureUrl());
        response.setLocale(user.getLocale());
        response.setTimezone(user.getTimezone());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}

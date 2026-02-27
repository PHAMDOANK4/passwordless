package org.openidentityplatform.passwordless.iam.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.openidentityplatform.passwordless.exceptions.NotFoundException;
import org.openidentityplatform.passwordless.iam.dto.CreateUserRequest;
import org.openidentityplatform.passwordless.iam.dto.UserResponse;
import org.openidentityplatform.passwordless.iam.services.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/domains/{domainId}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@PathVariable String domainId,
                                   @Valid @RequestBody CreateUserRequest request) throws NotFoundException {
        return userService.createUser(domainId, request);
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable String domainId,
                                @PathVariable String userId) throws NotFoundException {
        return userService.getUser(domainId, userId);
    }

    @GetMapping
    public Page<UserResponse> listUsers(@PathVariable String domainId, Pageable pageable) {
        return userService.listUsersByDomain(domainId, pageable);
    }

    @PostMapping("/{userId}/deactivate")
    public UserResponse deactivateUser(@PathVariable String domainId,
                                       @PathVariable String userId) throws NotFoundException {
        return userService.deactivateUser(domainId, userId);
    }

    @PostMapping("/{userId}/activate")
    public UserResponse activateUser(@PathVariable String domainId,
                                     @PathVariable String userId) throws NotFoundException {
        return userService.activateUser(domainId, userId);
    }
}

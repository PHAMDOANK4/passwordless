package org.openidentityplatform.passwordless.token.controllers;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.token.models.RefreshTokenRequest;
import org.openidentityplatform.passwordless.token.models.TokenPair;
import org.openidentityplatform.passwordless.token.models.TokenRefreshResponse;
import org.openidentityplatform.passwordless.token.services.InvalidRefreshTokenException;
import org.openidentityplatform.passwordless.token.services.RefreshTokenService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/token")
public class TokenController {

    private final RefreshTokenService refreshTokenService;

    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(@RequestBody @Valid RefreshTokenRequest request) throws InvalidRefreshTokenException {
        TokenPair pair = refreshTokenService.refresh(request.getRefreshToken());
        return new TokenRefreshResponse(
                pair.getAccessToken(),
                pair.getRefreshToken(),
                "Bearer",
                pair.getAccessTokenExpiresIn()
        );
    }
}

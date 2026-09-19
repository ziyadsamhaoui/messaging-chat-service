package com.ziyadsamhaoui.messagingchatservice.security;

import com.ziyadsamhaoui.messagingchatservice.exception.UnauthenticatedRequestException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CurrentUserProvider {

    public String requireUserId() {
        Jwt jwt = currentJwt()
                .orElseThrow(() -> new UnauthenticatedRequestException("A valid bearer token is required"));

        String subject = jwt.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new UnauthenticatedRequestException("The access token does not identify a user");
        }

        return subject;
    }

    public Optional<String> usernameClaim() {
        return currentJwt().map(jwt -> {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            return StringUtils.hasText(preferredUsername) ? preferredUsername : jwt.getClaimAsString("username");
        }).filter(StringUtils::hasText);
    }

    private Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && jwtAuthentication.isAuthenticated()) {
            return Optional.of(jwtAuthentication.getToken());
        }
        return Optional.empty();
    }
}

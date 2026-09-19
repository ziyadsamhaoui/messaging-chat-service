package com.ziyadsamhaoui.messagingchatservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-BadrLink-Internal-Token";

    private static final String INTERNAL_PRINCIPAL = "badrlink-internal-service";
    private static final String INTERNAL_AUTHORITY = "ROLE_INTERNAL";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final String expectedToken;

    public InternalServiceTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String providedToken = request.getHeader(HEADER_NAME);

        if (StringUtils.hasText(expectedToken) && StringUtils.hasText(providedToken)
                && matches(expectedToken, providedToken)) {

            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken
                    .authenticated(INTERNAL_PRINCIPAL, null, List.of(new SimpleGrantedAuthority(INTERNAL_AUTHORITY)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String expected, String provided) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}

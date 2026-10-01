package com.smartwatch.user.security;

import com.smartwatch.common.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stateless Bearer authentication. A valid access token still requires the
 * account to be enabled, so disabling a user takes effect immediately.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserAccountDetailsService userAccountDetailsService;
    private final ApiErrorWriter apiErrorWriter;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            UserAccountDetailsService userAccountDetailsService,
            ApiErrorWriter apiErrorWriter) {
        this.jwtTokenService = jwtTokenService;
        this.userAccountDetailsService = userAccountDetailsService;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!header.startsWith(BEARER_PREFIX)) {
            apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "Invalid access token");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "Invalid access token");
            return;
        }
        try {
            UUID userId = jwtTokenService.parseUserId(token);
            UserPrincipal principal = (UserPrincipal) userAccountDetailsService.loadUserByUsername(userId.toString());
            if (!principal.isEnabled()) {
                apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "Authentication is required");
                return;
            }
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    null,
                    principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (InvalidAccessTokenException exception) {
            SecurityContextHolder.clearContext();
            apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, exception.getMessage());
        } catch (UsernameNotFoundException exception) {
            SecurityContextHolder.clearContext();
            apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
    }
}

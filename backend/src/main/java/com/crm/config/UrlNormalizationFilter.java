package com.crm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Normalizes incoming request URIs to handle client misconfigurations gracefully
 * (e.g., when a client includes /health/ in its base API URL, turning requests into /api/v1/health/api/v1/auth/login).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UrlNormalizationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (shouldNormalize(uri)) {
            String normalizedUri = normalize(uri);
            HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
                @Override
                public String getRequestURI() {
                    return normalizedUri;
                }

                @Override
                public String getServletPath() {
                    return normalizedUri;
                }
            };
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldNormalize(String uri) {
        if (uri == null) return false;
        // Keep root and standard health endpoints intact
        if (uri.equals("/health") || uri.equals("/health/") ||
            uri.equals("/api/v1/health") || uri.equals("/api/v1/health/")) {
            return false;
        }
        return uri.contains("/health/api/v1/") ||
               uri.contains("/api/v1/health/api/v1/") ||
               uri.contains("/api/v1/health/auth/") ||
               uri.contains("/health/auth/") ||
               (uri.startsWith("/api/v1/health/") && uri.length() > "/api/v1/health/".length()) ||
               (uri.startsWith("/health/") && uri.length() > "/health/".length());
    }

    private String normalize(String uri) {
        String result = uri;
        if (result.contains("/health/api/v1/")) {
            result = result.replace("/health/api/v1/", "/api/v1/");
        }
        if (result.contains("/health/auth/")) {
            result = result.replace("/health/auth/", "/auth/");
        }
        if (result.startsWith("/api/v1/health/")) {
            result = "/api/v1/" + result.substring("/api/v1/health/".length());
        } else if (result.startsWith("/health/")) {
            result = "/" + result.substring("/health/".length());
        }

        // Deduplicate multiple /api/v1/ prefixes
        while (result.contains("/api/v1/api/v1/")) {
            result = result.replace("/api/v1/api/v1/", "/api/v1/");
        }

        // Deduplicate duplicate slashes
        result = result.replaceAll("/+", "/");
        return result;
    }
}

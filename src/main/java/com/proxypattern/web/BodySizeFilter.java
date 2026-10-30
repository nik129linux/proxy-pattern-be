package com.proxypattern.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects oversized service request bodies before they are read. */
@Component
public class BodySizeFilter extends OncePerRequestFilter {

    private static final long MAX_BYTES = 16 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/services/")
                && request.getContentLengthLong() > MAX_BYTES) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"error\":\"BODY_TOO_LARGE\",\"message\":\"request body exceeds 16 KB\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}

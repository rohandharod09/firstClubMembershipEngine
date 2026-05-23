package com.firstclub.membership.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates SLF4J MDC fields at the start of every request.
 *
 * Every log line emitted within the request thread will automatically include:
 *   correlationId — X-Correlation-ID header if provided, otherwise a new UUID
 *   requestPath   — HTTP method + path (e.g. POST /api/v1/subscriptions)
 *   userId        — X-User-ID header if present (for debugging user-specific issues)
 *
 * MDC is cleared after the response to prevent leakage into thread-pool threads.
 */
@Component
@Order(1)
public class MdcRequestFilter extends OncePerRequestFilter {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String USER_HEADER = "X-User-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String correlationId = request.getHeader(CORRELATION_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            MDC.put("correlationId", correlationId);
            MDC.put("requestPath", request.getMethod() + " " + request.getRequestURI());

            String userId = request.getHeader(USER_HEADER);
            if (userId != null && !userId.isBlank()) {
                MDC.put("userId", userId);
            }

            // Echo the correlationId back so callers can correlate their request
            response.setHeader(CORRELATION_HEADER, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}

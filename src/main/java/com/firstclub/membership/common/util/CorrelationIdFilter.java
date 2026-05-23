package com.firstclub.membership.common.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that injects a correlationId into MDC for every inbound request.
 * Uses X-Correlation-ID header if provided by the caller, otherwise generates a new UUID.
 * All log lines within the request automatically include correlationId.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_REQUEST_PATH = "requestPath";
    private static final String MDC_REQUEST_METHOD = "requestMethod";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_CORRELATION_ID, correlationId);
            MDC.put(MDC_REQUEST_PATH, request.getRequestURI());
            MDC.put(MDC_REQUEST_METHOD, request.getMethod());
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_REQUEST_PATH);
            MDC.remove(MDC_REQUEST_METHOD);
        }
    }
}

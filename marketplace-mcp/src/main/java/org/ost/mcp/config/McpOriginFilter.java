package org.ost.mcp.config;

import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.HeaderAccessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/** Answers the validator's status (403) with no body when a request carries an {@code Origin} outside the allowed list; a request without one passes. */
public class McpOriginFilter extends OncePerRequestFilter {

    private final DefaultServerTransportSecurityValidator validator;

    public McpOriginFilter(@NonNull List<String> allowedOrigins) {
        this.validator = DefaultServerTransportSecurityValidator.builder().allowedOrigins(allowedOrigins).build();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            validator.validate(headersOf(request));
        } catch (ServerTransportSecurityException e) {
            response.setStatus(e.getStatusCode());
            return;
        }
        chain.doFilter(request, response);
    }

    private static HeaderAccessor headersOf(HttpServletRequest request) {
        return new HeaderAccessor() {
            @Override
            public List<String> getHeader(String name) {
                return Collections.list(request.getHeaders(name));
            }

            @Override
            public List<String> getHeaderNames() {
                return Collections.list(request.getHeaderNames());
            }
        };
    }
}

package org.ost.mcp.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/** Reads the {@code Authorization} header of the MCP request currently being served, if any. */
@Component
public class CallerAuthorization {

    public Optional<String> header() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .map((HttpServletRequest request) -> request.getHeader(HttpHeaders.AUTHORIZATION));
    }
}

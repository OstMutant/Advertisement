package org.ost.mcp.rest;

import org.springframework.http.HttpHeaders;

/** One REST API response reduced to what a tool needs: status code, headers and raw body text. */
public record RestAnswer(int status, HttpHeaders headers, String body) {

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}

package org.ost.mcptest;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Binds a current request carrying the given Authorization header, as the REST client reads it from the request context. */
@UtilityClass
public class CallerRequest {

    public void authorizeAs(@NonNull String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}

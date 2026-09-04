package org.ost.restapi.config;

import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the two real auth mechanisms {@link ApiSecurityConfig} enforces, so Swagger UI's own
 * "Authorize" button matches how this API actually authenticates rather than a generic default.
 */
@Configuration
@SecurityScheme(name = "bearerKey", type = SecuritySchemeType.HTTP, scheme = "bearer", in = SecuritySchemeIn.HEADER,
        description = "API key issued via POST /api/api-keys, sent as 'Authorization: Bearer <key>'.")
@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic", in = SecuritySchemeIn.HEADER,
        description = "Account email/password — used only to issue a new API key via POST /api/api-keys.")
public class OpenApiConfig {
}

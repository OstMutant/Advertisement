package org.ost.restapi;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @Operation(summary = "Liveness check", description = "Always returns \"ok\" when the application is up -- no auth, no dependencies checked.")
    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}

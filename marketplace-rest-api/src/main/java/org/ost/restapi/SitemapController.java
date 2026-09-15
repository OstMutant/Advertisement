package org.ost.restapi;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.SitemapService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Top-level, unauthenticated sitemap feed -- outside {@code /api/**}, not subject to {@code ApiSecurityConfig}. */
@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final SitemapService sitemapService;

    @Operation(summary = "Sitemap XML for search engines", description = "Lists every active advertisement and provider profile URL. Cached (15-minute TTL), invalidated on save/delete.")
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        return sitemapService.getSitemap();
    }
}

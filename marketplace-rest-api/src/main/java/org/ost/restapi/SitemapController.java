package org.ost.restapi;

import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.SitemapService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final SitemapService sitemapService;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        return sitemapService.getSitemap();
    }
}

package at.v3rtumnus.planman.controller.ui;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the web app manifest and the service worker from the site root: a service worker
 * only controls pages at or below its own path, so it cannot live under /resources/.
 */
@Controller
public class PwaController {

    private static final MediaType MANIFEST = MediaType.parseMediaType("application/manifest+json");
    private static final MediaType JAVASCRIPT = MediaType.parseMediaType("text/javascript");

    @GetMapping("/manifest.webmanifest")
    public ResponseEntity<Resource> manifest() {
        return ResponseEntity.ok()
                .contentType(MANIFEST)
                .cacheControl(CacheControl.noCache())
                .body(new ClassPathResource("pwa/manifest.webmanifest"));
    }

    @GetMapping("/sw.js")
    public ResponseEntity<Resource> serviceWorker() {
        return ResponseEntity.ok()
                .contentType(JAVASCRIPT)
                .cacheControl(CacheControl.noCache())
                .body(new ClassPathResource("pwa/sw.js"));
    }
}

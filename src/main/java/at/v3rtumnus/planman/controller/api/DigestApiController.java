package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.notification.DigestDTO;
import at.v3rtumnus.planman.service.health.HealthDigestService;
import at.v3rtumnus.planman.service.DepotDigestService;
import at.v3rtumnus.planman.service.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * {@code GET /api/digest} for n8n (Morning Briefing, Glance). n8n calls plan-man directly
 * inside dockernet with a bearer token instead of an Authelia login; an empty token turns
 * the endpoint off.
 */
@RestController
public class DigestApiController {

    private final NotificationService notificationService;
    private final HealthDigestService healthDigestService;
    private final DepotDigestService depotDigestService;
    private final byte[] token;

    public DigestApiController(NotificationService notificationService, HealthDigestService healthDigestService,
                               DepotDigestService depotDigestService, @Value("${planman.digest.token:}") String token) {
        this.notificationService = notificationService;
        this.healthDigestService = healthDigestService;
        this.depotDigestService = depotDigestService;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/api/digest")
    public ResponseEntity<DigestDTO> digest(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (token.length == 0) {
            return ResponseEntity.notFound().build();
        }
        String presented = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        if (!MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(healthDigestService.withHealth(notificationService.digest())
                .withDepot(depotDigestService.week().orElse(null)));
    }
}

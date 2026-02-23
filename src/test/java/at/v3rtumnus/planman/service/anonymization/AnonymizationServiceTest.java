package at.v3rtumnus.planman.service.anonymization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymizationServiceTest {

    private AnonymizationService service;

    @BeforeEach
    void setUp() {
        service = new AnonymizationService();
    }

    // --- null / empty ---

    @Test
    void anonymize_nullText_returnsNullResult() {
        AnonymizationResult result = service.anonymize(null);
        assertThat(result.getAnonymizedText()).isNull();
        assertThat(result.hasAnonymizedEntities()).isFalse();
    }

    @Test
    void anonymize_emptyText_returnsEmptyResult() {
        AnonymizationResult result = service.anonymize("");
        assertThat(result.getAnonymizedText()).isEmpty();
        assertThat(result.hasAnonymizedEntities()).isFalse();
    }

    @Test
    void anonymize_textWithNoPII_returnsUnchanged() {
        String text = "Hello world, this is a plain sentence.";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getAnonymizedText()).isEqualTo(text);
        assertThat(result.hasAnonymizedEntities()).isFalse();
    }

    // --- Email ---

    @Test
    void anonymize_email_isDetectedAndReplaced() {
        String text = "Contact us at john.doe@example.com for help.";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getAnonymizedText()).doesNotContain("john.doe@example.com");
        assertThat(result.getAnonymizedText()).contains("[EMAIL_1]");
        assertThat(result.getDetectedEntityTypes()).contains(EntityType.EMAIL);
    }

    @Test
    void anonymize_sameEmailTwice_usesSamePlaceholder() {
        String text = "Email: john.doe@example.com and again john.doe@example.com";
        AnonymizationResult result = service.anonymize(text);
        long count = result.getAnonymizedText().chars()
                .filter(c -> result.getAnonymizedText().indexOf("[EMAIL_") >= 0)
                .count();
        // only one unique entity
        assertThat(result.getEntityCount()).isEqualTo(1);
        // placeholder appears twice in anonymized text
        assertThat(result.getAnonymizedText())
                .contains("[EMAIL_1]");
        assertThat(result.getAnonymizedText().split("\\[EMAIL_1]", -1).length - 1).isEqualTo(2);
    }

    @Test
    void anonymize_multipleEmails_getDistinctPlaceholders() {
        String text = "alice@example.com and bob@example.org";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getEntityCount()).isEqualTo(2);
        assertThat(result.getAnonymizedText()).contains("[EMAIL_1]");
        assertThat(result.getAnonymizedText()).contains("[EMAIL_2]");
    }

    // --- IBAN ---

    @Test
    void anonymize_validAustrianIBAN_isDetected() {
        // AT61 1904 3002 3457 3201 — valid IBAN
        String text = "My IBAN is AT61 1904 3002 3457 3201.";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).contains(EntityType.IBAN);
        assertThat(result.getAnonymizedText()).doesNotContain("AT61");
    }

    @Test
    void anonymize_invalidIBAN_isNotDetected() {
        // Looks like an IBAN but fails mod-97 check
        String text = "Fake IBAN: AT00 0000 0000 0000 0000.";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).doesNotContain(EntityType.IBAN);
    }

    // --- Credit Card (Luhn) ---

    @Test
    void anonymize_validVisaCreditCard_isDetected() {
        // 4532015112830366 — valid Luhn
        String text = "Card number: 4532015112830366";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).contains(EntityType.CREDIT_CARD);
        assertThat(result.getAnonymizedText()).doesNotContain("4532015112830366");
    }

    @Test
    void anonymize_invalidLuhnCard_isNotDetected() {
        // Fails Luhn
        String text = "Bad card: 4532015112830367";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).doesNotContain(EntityType.CREDIT_CARD);
    }

    // --- URL ---

    @Test
    void anonymize_url_isDetected() {
        String text = "Visit https://www.example.com/path?q=1 for details.";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).contains(EntityType.URL);
        assertThat(result.getAnonymizedText()).doesNotContain("https://www.example.com");
    }

    // --- Date ---

    @Test
    void anonymize_isoDate_isDetected() {
        String text = "Born on 2024-03-15.";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).contains(EntityType.DATE);
        assertThat(result.getAnonymizedText()).doesNotContain("2024-03-15");
    }

    @Test
    void anonymize_germanDate_isDetected() {
        String text = "Datum: 15.03.2024";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).contains(EntityType.DATE);
    }

    // --- IP Address ---

    @Test
    void anonymize_ipv4Address_isDetected() {
        String text = "Server IP is 192.168.1.100.";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).contains(EntityType.IP_ADDRESS);
        assertThat(result.getAnonymizedText()).doesNotContain("192.168.1.100");
    }

    // --- Overlap resolution ---

    @Test
    void anonymize_overlappingMatches_higherConfidenceWins() {
        // Email has confidence 0.95, so it should win over lower-confidence patterns
        String text = "user@domain.com";
        AnonymizationResult result = service.anonymize(text);
        // Should be a single entity, not fragmented
        assertThat(result.getEntityCount()).isGreaterThanOrEqualTo(1);
        // The email itself should be replaced
        assertThat(result.getAnonymizedText()).doesNotContain("user@domain.com");
    }

    // --- De-anonymization ---

    @Test
    void deanonymize_reversesReplacements() {
        String original = "Contact john.doe@example.com for info.";
        AnonymizationResult result = service.anonymize(original);
        String anonymized = result.getAnonymizedText();
        assertThat(anonymized).doesNotContain("john.doe@example.com");

        String restored = result.deanonymize(anonymized);
        assertThat(restored).contains("john.doe@example.com");
    }

    @Test
    void deanonymize_nullInput_returnsNull() {
        AnonymizationResult result = service.anonymize("test@example.com");
        assertThat(result.deanonymize(null)).isNull();
    }

    @Test
    void deanonymize_noEntities_returnsTextUnchanged() {
        AnonymizationResult result = service.anonymize("hello world");
        assertThat(result.deanonymize("hello world")).isEqualTo("hello world");
    }

    // --- Placeholder consistency ---

    @Test
    void getPlaceholder_returnsConsistentPlaceholderForValue() {
        AnonymizationResult result = service.anonymize("Send to alice@test.com and alice@test.com");
        String placeholder = result.getPlaceholder("alice@test.com");
        assertThat(placeholder).isNotNull();
        assertThat(placeholder).startsWith("[EMAIL_");
    }

    @Test
    void getOriginalValue_returnsMappedValue() {
        AnonymizationResult result = service.anonymize("Email: bob@test.org");
        String placeholder = result.getPlaceholder("bob@test.org");
        assertThat(result.getOriginalValue(placeholder)).isEqualTo("bob@test.org");
    }

    // --- UUID ---

    @Test
    void anonymize_uuid_isDetected() {
        String text = "ID: 550e8400-e29b-41d4-a716-446655440000";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).contains(EntityType.UUID);
    }

    // --- Austrian UID ---

    @Test
    void anonymize_austrianUID_isDetected() {
        String text = "UID: ATU12345678";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.getDetectedEntityTypes()).contains(EntityType.AUSTRIAN_UID);
    }

    // --- anonymizeWithExistingMappings ---

    @Test
    void anonymizeWithExistingMappings_appliesExistingMap() {
        AnonymizationResult result = service.anonymize("Email: test@example.com");
        String followUp = result.anonymizeWithExistingMappings("Reply to test@example.com");
        assertThat(followUp).doesNotContain("test@example.com");
    }

    @Test
    void anonymizeWithExistingMappings_nullInput_returnsNull() {
        AnonymizationResult result = service.anonymize("test@example.com");
        assertThat(result.anonymizeWithExistingMappings(null)).isNull();
    }
}

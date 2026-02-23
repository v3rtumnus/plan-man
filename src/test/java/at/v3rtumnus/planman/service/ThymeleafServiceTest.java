package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.entity.finance.FinancialTransactionType;
import at.v3rtumnus.planman.entity.finance.Interval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafServiceTest {

    private ThymeleafService service;

    @BeforeEach
    void setUp() {
        service = new ThymeleafService();
    }

    // --- formatTransactionType ---

    static Stream<Arguments> transactionTypeProvider() {
        return Stream.of(
                Arguments.of(FinancialTransactionType.BUY, "Kauf"),
                Arguments.of(FinancialTransactionType.SELL, "Verkauf"),
                Arguments.of(FinancialTransactionType.SAVINGS_PLAN, "Sparplan"),
                Arguments.of(FinancialTransactionType.GIFT, "Geschenk"),
                Arguments.of(FinancialTransactionType.SPIN_OFF, "Merge"),
                Arguments.of(FinancialTransactionType.SPLIT, "Split"),
                Arguments.of(FinancialTransactionType.DIVIDEND, "Dividende")
        );
    }

    @ParameterizedTest
    @MethodSource("transactionTypeProvider")
    void formatTransactionType_mapsToGermanLabel(FinancialTransactionType type, String expected) {
        assertThat(service.formatTransactionType(type)).isEqualTo(expected);
    }

    // --- formatInterval ---

    static Stream<Arguments> intervalProvider() {
        return Stream.of(
                Arguments.of(Interval.MONTHLY, "monatlich"),
                Arguments.of(Interval.QUARTERLY, "quartalsweise"),
                Arguments.of(Interval.YEARLY, "j&auml;hrlich")
        );
    }

    @ParameterizedTest
    @MethodSource("intervalProvider")
    void formatInterval_mapsToGermanLabel(Interval interval, String expected) {
        assertThat(service.formatInterval(interval)).isEqualTo(expected);
    }

    // --- formatNumber ---

    @Test
    void formatNumber_nullValue_returnsDash() {
        assertThat(service.formatNumber(null, 2, "€")).isEqualTo("-");
    }

    @Test
    void formatNumber_zeroValue_returnsDash() {
        assertThat(service.formatNumber(BigDecimal.ZERO, 2, "€")).isEqualTo("-");
    }

    @Test
    void formatNumber_withSuffix_appendsSuffix() {
        String result = service.formatNumber(new BigDecimal("1234.56"), 2, "€");
        assertThat(result).endsWith(" €");
        assertThat(result).contains("1");
    }

    @Test
    void formatNumber_withoutSuffix_noTrailingSpace() {
        String result = service.formatNumber(new BigDecimal("42.5"), 1, null);
        assertThat(result).doesNotEndWith(" ");
    }

    @Test
    void formatNumber_zeroDecimalDigits_noDecimalPoint() {
        String result = service.formatNumber(new BigDecimal("100"), 0, null);
        assertThat(result).doesNotContain(".");
        assertThat(result).doesNotContain(",");
    }

    @Test
    void formatNumber_twoDecimalDigits_formatsCorrectly() {
        String result = service.formatNumber(new BigDecimal("9.99"), 2, null);
        // Result should have 2 fractional digits
        assertThat(result).matches(".*[,.]\\d{2}");
    }

    // --- getNumberClass ---

    @Test
    void getNumberClass_positiveNumber_returnsPositiveClass() {
        assertThat(service.getNumberClass(new BigDecimal("100"))).isEqualTo("financial-value-positive");
    }

    @Test
    void getNumberClass_negativeNumber_returnsNegativeClass() {
        assertThat(service.getNumberClass(new BigDecimal("-50"))).isEqualTo("financial-value-negative");
    }

    @Test
    void getNumberClass_zero_returnsCenteredClass() {
        assertThat(service.getNumberClass(BigDecimal.ZERO)).isEqualTo("text-center");
    }

    @Test
    void getNumberClass_null_returnsCenteredClass() {
        assertThat(service.getNumberClass(null)).isEqualTo("text-center");
    }
}

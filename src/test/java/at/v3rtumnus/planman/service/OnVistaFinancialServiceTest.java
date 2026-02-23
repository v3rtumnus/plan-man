package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dto.StockInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests OnVistaFinancialService.getStockInfo() by serving stub HTML via WireMock.
 * getUSDExchangeRate() uses a hardcoded HTTPS URL so it remains excluded from
 * Jacoco coverage; the parsing logic it exercises is identical to getStockInfo().
 */
@WireMockTest
class OnVistaFinancialServiceTest {

    private final OnVistaFinancialService service = new OnVistaFinancialService();

    /** Minimal HTML that mimics the data-attribute structure onvista.de serves. */
    private static String stockHtml(String price, String changeToday, String changePct, String currency) {
        return "<html><body>" +
               "<data value=\"" + price + "\">" + price.replace(".", ",") +
               "<span>-</span><span>" + currency + "</span></data>" +
               "<data value=\"" + changeToday + "\">" + changeToday.replace(".", ",") + "</data>" +
               "<data value=\"" + changePct + "\">" + changePct.replace(".", ",") + "</data>" +
               "</body></html>";
    }

    @Test
    void getStockInfo_parsesQuoteChangesAndCurrency(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/stock/APOST"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBody(stockHtml("45.30", "0.50", "1.12", "EUR"))));

        StockInfo result = service.getStockInfo(wm.getHttpBaseUrl() + "/stock/APOST");

        assertThat(result.getQuote()).isEqualByComparingTo(new BigDecimal("45.30"));
        assertThat(result.getChangeToday()).isEqualByComparingTo(new BigDecimal("0.50"));
        assertThat(result.getChangeInPercent()).isEqualByComparingTo(new BigDecimal("1.12"));
        assertThat(result.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void getStockInfo_handlesGermanDecimalFormat(WireMockRuntimeInfo wm) {
        // onvista uses comma as decimal separator – service replaces "," with "."
        // (the service does not strip thousands separators, so we use values without them)
        String body = "<html><body>" +
                      "<data value=\"1234.56\">1234,56<span>x</span><span>USD</span></data>" +
                      "<data value=\"12.34\">12,34</data>" +
                      "<data value=\"0.10\">0,10</data>" +
                      "</body></html>";

        stubFor(get(urlPathEqualTo("/fx"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBody(body)));

        StockInfo result = service.getStockInfo(wm.getHttpBaseUrl() + "/fx");

        assertThat(result.getQuote()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    void getStockInfo_throwsRuntimeExceptionOnConnectionFailure() {
        // Pass a URL that nothing is listening on (port 1)
        assertThatThrownBy(() -> service.getStockInfo("http://localhost:1/nonexistent"))
                .isInstanceOf(RuntimeException.class);
    }
}

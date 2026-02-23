package at.v3rtumnus.planman.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForeignExchangeServiceTest {

    @Mock
    private OnVistaFinancialService onVistaFinancialService;

    @InjectMocks
    private ForeignExchangeService fxService;

    @Test
    void getFxRate_delegatesToOnVistaService() throws IOException {
        when(onVistaFinancialService.getUSDExchangeRate()).thenReturn(new BigDecimal("1.08"));

        BigDecimal rate = fxService.getFxRate("USD");

        assertThat(rate).isEqualByComparingTo(new BigDecimal("1.08"));
        verify(onVistaFinancialService).getUSDExchangeRate();
    }

    @Test
    void getFxRate_differentCurrencyCodes_allDelegateToSameMethod() throws IOException {
        when(onVistaFinancialService.getUSDExchangeRate()).thenReturn(new BigDecimal("1.05"));

        // The method ignores the fx parameter and always calls getUSDExchangeRate
        fxService.getFxRate("EUR");
        fxService.getFxRate("GBP");

        verify(onVistaFinancialService, times(2)).getUSDExchangeRate();
    }
}

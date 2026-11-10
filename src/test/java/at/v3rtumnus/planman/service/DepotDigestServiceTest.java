package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.FinancialSnapshotRepository;
import at.v3rtumnus.planman.dao.FinancialTransactionRepository;
import at.v3rtumnus.planman.dto.notification.DigestDTO.DepotWeek;
import at.v3rtumnus.planman.entity.finance.FinancialSnapshot;
import at.v3rtumnus.planman.entity.finance.FinancialTransaction;
import at.v3rtumnus.planman.entity.finance.FinancialTransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DepotDigestServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private final Clock clock = Clock.fixed(TODAY.atTime(6, 0).atZone(ZONE).toInstant(), ZONE);
    private final FinancialSnapshotRepository snapshots = mock(FinancialSnapshotRepository.class);
    private final FinancialTransactionRepository transactions = mock(FinancialTransactionRepository.class);
    private final DepotDigestService service = new DepotDigestService(snapshots, transactions, clock);

    private static FinancialSnapshot snapshot(LocalDate date, String shares, String funds, String etf) {
        return new FinancialSnapshot(date, new BigDecimal(shares), funds == null ? null : new BigDecimal(funds),
                new BigDecimal(etf), new BigDecimal("30000"), new BigDecimal("-277000"));
    }

    private static FinancialTransaction tx(FinancialTransactionType type, String amount) {
        return new FinancialTransaction(TODAY.minusDays(2), amount == null ? null : new BigDecimal(amount),
                BigDecimal.ONE, BigDecimal.ZERO, type, null);
    }

    @Test
    void weekNetOfBuysAndSales() {
        when(snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(TODAY))
                .thenReturn(Optional.of(snapshot(TODAY, "116000", "1300", "38000")));
        when(snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(TODAY.minusDays(7)))
                .thenReturn(Optional.of(snapshot(LocalDate.of(2026, 11, 3), "115000", null, "37000")));
        when(transactions.findByTransactionDateAfterAndTransactionDateLessThanEqual(LocalDate.of(2026, 11, 3), TODAY))
                .thenReturn(List.of(tx(FinancialTransactionType.BUY, "1000"), tx(FinancialTransactionType.SELL, "200"),
                        tx(FinancialTransactionType.GIFT, "0"), tx(FinancialTransactionType.BUY, null)));

        DepotWeek week = service.week().orElseThrow();

        assertThat(week.from()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(week.valueFrom()).isEqualByComparingTo("152000");
        assertThat(week.valueTo()).isEqualByComparingTo("155300");
        assertThat(week.netFlows()).isEqualByComparingTo("800");
        assertThat(week.change()).isEqualByComparingTo("2500");
        assertThat(week.changePercent()).isEqualByComparingTo("1.6");
        assertThat(week.line()).isEqualTo("Depot seit 3. Nov.: +1,6 % (+2 500 €), jetzt 155 300 €. Käufe/Verkäufe (+800 €) herausgerechnet.");
    }

    @Test
    void lossWithoutFlows() {
        when(snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(TODAY))
                .thenReturn(Optional.of(snapshot(TODAY, "90000", "0", "10000")));
        when(snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(TODAY.minusDays(7)))
                .thenReturn(Optional.of(snapshot(TODAY.minusDays(7), "95000", "0", "10500")));
        when(transactions.findByTransactionDateAfterAndTransactionDateLessThanEqual(TODAY.minusDays(7), TODAY)).thenReturn(List.of());

        assertThat(service.week()).get().extracting(DepotWeek::line)
                .isEqualTo("Depot seit 3. Nov.: −5,2 % (−5 500 €), jetzt 100 000 €.");
    }

    @Test
    void noSnapshotsOrEmptyBase_noLine() {
        when(snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(TODAY)).thenReturn(Optional.empty());
        assertThat(service.week()).isEmpty();

        when(snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(TODAY))
                .thenReturn(Optional.of(snapshot(TODAY, "1", "0", "0")));
        when(snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(TODAY.minusDays(7))).thenReturn(Optional.empty());
        assertThat(service.week()).isEmpty();

        when(snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(TODAY.minusDays(7)))
                .thenReturn(Optional.of(snapshot(TODAY.minusDays(7), "0", "0", "0")));
        assertThat(service.week()).isEmpty();
    }
}

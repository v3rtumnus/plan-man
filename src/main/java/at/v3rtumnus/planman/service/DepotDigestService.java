package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.FinancialSnapshotRepository;
import at.v3rtumnus.planman.dao.FinancialTransactionRepository;
import at.v3rtumnus.planman.dto.notification.DigestDTO.DepotWeek;
import at.v3rtumnus.planman.entity.finance.FinancialSnapshot;
import at.v3rtumnus.planman.entity.finance.FinancialTransaction;
import at.v3rtumnus.planman.entity.finance.FinancialTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * The depot's last week for the briefing: latest daily snapshot against the one a week
 * before, net of buys and sales booked in between (a buy is not a gain). Gifts carry no
 * amount and are not counted; a buy imported late shows up as a gain in that week.
 */
@Service
@RequiredArgsConstructor
public class DepotDigestService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN);

    private final FinancialSnapshotRepository snapshots;
    private final FinancialTransactionRepository transactions;
    private final Clock clock;

    public Optional<DepotWeek> week() {
        Optional<FinancialSnapshot> latest = snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(LocalDate.now(clock));
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        LocalDate to = latest.get().getSnapshotDate();
        Optional<FinancialSnapshot> base = snapshots.findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(to.minusDays(7));
        if (base.isEmpty()) {
            return Optional.empty();
        }
        LocalDate from = base.get().getSnapshotDate();
        BigDecimal valueFrom = depot(base.get());
        BigDecimal valueTo = depot(latest.get());
        if (valueFrom.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal flows = BigDecimal.ZERO;
        for (FinancialTransaction t : transactions.findByTransactionDateAfterAndTransactionDateLessThanEqual(from, to)) {
            if (t.getAmount() == null) {
                continue;
            }
            if (t.getTransactionType() == FinancialTransactionType.BUY) {
                flows = flows.add(t.getAmount());
            } else if (t.getTransactionType() == FinancialTransactionType.SELL) {
                flows = flows.subtract(t.getAmount());
            }
        }
        BigDecimal change = valueTo.subtract(valueFrom).subtract(flows);
        BigDecimal percent = change.multiply(BigDecimal.valueOf(100)).divide(valueFrom, MathContext.DECIMAL64)
                .setScale(1, RoundingMode.HALF_UP);
        String line = "Depot seit " + DAY.format(from) + ": " + signedPercent(percent) + " (" + signedEuro(change)
                + "), jetzt " + euro(valueTo) + "."
                + (flows.signum() == 0 ? "" : " Käufe/Verkäufe (" + signedEuro(flows) + ") herausgerechnet.");
        return Optional.of(new DepotWeek(from, to, valueFrom, valueTo, flows,
                change.setScale(2, RoundingMode.HALF_UP), percent, line));
    }

    private static BigDecimal depot(FinancialSnapshot s) {
        return nz(s.getSharesSum()).add(nz(s.getFundsSum())).add(nz(s.getEtfSum()));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static String signedPercent(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(1);
        return (value.signum() < 0 ? "−" : "+") + format.format(value.abs()) + " %";
    }

    static String signedEuro(BigDecimal value) {
        return (value.signum() < 0 ? "−" : "+") + euro(value.abs());
    }

    /** Whole euros with a space as thousands separator: "155 662 €". */
    static String euro(BigDecimal value) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.GERMANY);
        return format.format(value.setScale(0, RoundingMode.HALF_UP)).replace('.', ' ') + " €";
    }
}

package at.v3rtumnus.planman.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;

public class DateUtil {

    public static LocalDate getNextInstallmentDate(LocalDate currentDate) {
        LocalDate nextInstallmentDate = currentDate.plusMonths(1).withDayOfMonth(1);

        LocalDate firstInstallmentDate = LocalDate.of(2019, 4, 1);
        if (nextInstallmentDate.isBefore(firstInstallmentDate)) {
            nextInstallmentDate = firstInstallmentDate;
        }

        return nextInstallmentDate;
    }

    public static LocalDate getEndOfQuarter(LocalDate currentDate) {
        LocalDate firstDayOfQuarter = currentDate.with(currentDate.getMonth().firstMonthOfQuarter())
                .with(TemporalAdjusters.firstDayOfMonth());

        return firstDayOfQuarter.plusMonths(2)
                .with(TemporalAdjusters.lastDayOfMonth());
    }
}
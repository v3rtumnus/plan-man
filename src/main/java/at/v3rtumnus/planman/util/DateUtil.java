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

        while (!isWorkingDay(nextInstallmentDate)) {
            nextInstallmentDate = nextInstallmentDate.plusDays(1);
        }

        return nextInstallmentDate;
    }

    public static LocalDate getEndOfQuarter(LocalDate currentDate) {
        LocalDate firstDayOfQuarter = currentDate.with(currentDate.getMonth().firstMonthOfQuarter())
                .with(TemporalAdjusters.firstDayOfMonth());

        LocalDate endOfQuarter = firstDayOfQuarter.plusMonths(2)
                .with(TemporalAdjusters.lastDayOfMonth());

        while (!isWorkingDay(endOfQuarter)) {
            endOfQuarter = endOfQuarter.minusDays(1);
        }

        return endOfQuarter;
    }

    private static boolean isWorkingDay(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }

        //1st of January
        if (date.getDayOfMonth() == 1 && date.getMonth() == Month.JANUARY) {
            return false;
        }

        //Hl. drei Könige
        if (date.getDayOfMonth() == 6 && date.getMonth() == Month.JANUARY) {
            return false;
        }

        //1st of May
        if (date.getDayOfMonth() == 1 && date.getMonth() == Month.MAY) {
            return false;
        }

        //Mariä Himmelfahrt
        if (date.getDayOfMonth() == 15 && date.getMonth() == Month.AUGUST) {
            return false;
        }

        //National holiday
        if (date.getDayOfMonth() == 26 && date.getMonth() == Month.OCTOBER) {
            return false;
        }

        //Allerheiligen
        if (date.getDayOfMonth() == 1 && date.getMonth() == Month.NOVEMBER) {
            return false;
        }

        //Maria Empfängnis
        if (date.getDayOfMonth() == 8 && date.getMonth() == Month.DECEMBER) {
            return false;
        }

        //Christmas
        if ((date.getDayOfMonth() == 24 || date.getDayOfMonth() == 25 || date.getDayOfMonth() == 26) &&
                date.getMonth() == Month.DECEMBER) {
            return false;
        }

        LocalDate easterSunday = getEasterSunday(date.getYear());

        //Easter Monday
        if (date.equals(easterSunday.plusDays(1))) {
            return false;
        }

        //Christi Himmelfahrt
        if (date.equals(easterSunday.plusDays(39))) {
            return false;
        }

        //Pfingsten
        if (date.equals(easterSunday.plusDays(49))) {
            return false;
        }

        //Fronleichnam
        if (date.equals(easterSunday.plusDays(60))) {
            return false;
        }

        return true;
    }

    private static LocalDate getEasterSunday(int year) {
        int a = year % 19;
        int b = year % 4;
        int c = year % 7;
        int k = year / 100;
        int p = (13 + 8 * k) / 25;
        int q = k / 4;
        int M = (15 - p + k - q) % 30;
        int N = (4 + k - q) % 7;
        int d = (19 * a + M) % 30;
        int e = (2 * b + 4 * c + 6 * d + N) % 7;

        if (d == 29 && e == 6) {
            return LocalDate.of(year, 3, 22).plusDays(d + e).minusDays(7);
        } else
            return LocalDate.of(year, 3, 22).plusDays(d + e);
    }
}
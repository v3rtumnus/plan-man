package at.v3rtumnus.planman.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilTest {

    // --- getNextInstallmentDate ---

    @Test
    void getNextInstallmentDate_returnsFirstDayOfNextMonth() {
        LocalDate input = LocalDate.of(2024, 6, 15);
        LocalDate result = DateUtil.getNextInstallmentDate(input);
        assertThat(result).isEqualTo(LocalDate.of(2024, 7, 1));
    }

    @Test
    void getNextInstallmentDate_endOfMonth_returnsFirstOfNextMonth() {
        LocalDate input = LocalDate.of(2024, 1, 31);
        LocalDate result = DateUtil.getNextInstallmentDate(input);
        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 1));
    }

    @Test
    void getNextInstallmentDate_beforeFirstInstallmentDate_returnsFirstInstallmentDate() {
        // First installment date is 2019-04-01; any date whose next-month-first is before that
        // should return 2019-04-01
        LocalDate input = LocalDate.of(2019, 2, 1);
        LocalDate result = DateUtil.getNextInstallmentDate(input);
        assertThat(result).isEqualTo(LocalDate.of(2019, 4, 1));
    }

    @Test
    void getNextInstallmentDate_exactlyAtFirstInstallmentDate_returnsNextMonth() {
        // 2019-04-01 + 1 month = 2019-05-01, which is after 2019-04-01 so no floor applied
        LocalDate input = LocalDate.of(2019, 4, 1);
        LocalDate result = DateUtil.getNextInstallmentDate(input);
        assertThat(result).isEqualTo(LocalDate.of(2019, 5, 1));
    }

    @Test
    void getNextInstallmentDate_decemberWrapsToJanuary() {
        LocalDate input = LocalDate.of(2024, 12, 5);
        LocalDate result = DateUtil.getNextInstallmentDate(input);
        assertThat(result).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    // --- getEndOfQuarter ---

    static Stream<Arguments> quarterEndProvider() {
        return Stream.of(
                // Q1: Jan–Mar → ends 2024-03-31
                Arguments.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31)),
                Arguments.of(LocalDate.of(2024, 2, 15), LocalDate.of(2024, 3, 31)),
                Arguments.of(LocalDate.of(2024, 3, 31), LocalDate.of(2024, 3, 31)),
                // Q2: Apr–Jun → ends 2024-06-30
                Arguments.of(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30)),
                Arguments.of(LocalDate.of(2024, 5, 20), LocalDate.of(2024, 6, 30)),
                Arguments.of(LocalDate.of(2024, 6, 30), LocalDate.of(2024, 6, 30)),
                // Q3: Jul–Sep → ends 2024-09-30
                Arguments.of(LocalDate.of(2024, 7, 4), LocalDate.of(2024, 9, 30)),
                Arguments.of(LocalDate.of(2024, 9, 1), LocalDate.of(2024, 9, 30)),
                // Q4: Oct–Dec → ends 2024-12-31
                Arguments.of(LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31)),
                Arguments.of(LocalDate.of(2024, 12, 31), LocalDate.of(2024, 12, 31))
        );
    }

    @ParameterizedTest
    @MethodSource("quarterEndProvider")
    void getEndOfQuarter_returnsLastDayOfCurrentQuarter(LocalDate input, LocalDate expected) {
        assertThat(DateUtil.getEndOfQuarter(input)).isEqualTo(expected);
    }
}

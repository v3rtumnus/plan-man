package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.NutritionDay;
import at.v3rtumnus.planman.entity.health.MealSlot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MealSlotTest {

    @Test
    void at_mapsTimeOfDayToSlot() {
        assertThat(MealSlot.at(LocalTime.of(6, 0))).isEqualTo(MealSlot.BREAKFAST);
        assertThat(MealSlot.at(LocalTime.of(10, 45))).isEqualTo(MealSlot.SNACK);
        assertThat(MealSlot.at(LocalTime.of(12, 40))).isEqualTo(MealSlot.LUNCH);
        assertThat(MealSlot.at(LocalTime.of(16, 0))).isEqualTo(MealSlot.SNACK);
        assertThat(MealSlot.at(LocalTime.of(19, 30))).isEqualTo(MealSlot.DINNER);
    }

    @Test
    void nutritionDay_withoutTargets_hasNoLeftValues() {
        NutritionDay day = new NutritionDay(null, null, 500, new BigDecimal("20.0"), List.of());
        assertThat(day.kcalLeft()).isNull();
        assertThat(day.proteinLeftG()).isNull();
        assertThat(day.kcalPercent()).isZero();
    }

    @Test
    void nutritionDay_capsPercentAndProteinLeft() {
        NutritionDay day = new NutritionDay(2000, 160, 2500, new BigDecimal("170.0"), List.of());
        assertThat(day.kcalLeft()).isEqualTo(-500);
        assertThat(day.kcalPercent()).isEqualTo(100);
        assertThat(day.proteinLeftG()).isZero();
        assertThat(day.proteinPercent()).isEqualTo(100);
    }
}

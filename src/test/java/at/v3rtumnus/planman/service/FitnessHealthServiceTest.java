package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.fitness.FitnessMealLogRepository;
import at.v3rtumnus.planman.dao.fitness.FitnessProfileRepository;
import at.v3rtumnus.planman.dao.fitness.FitnessWeightLogRepository;
import at.v3rtumnus.planman.dto.fitness.FitnessHealthProfileDTO;
import at.v3rtumnus.planman.dto.fitness.WeightLogDTO;
import at.v3rtumnus.planman.entity.fitness.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitnessHealthServiceTest {

    @Mock private FitnessService fitnessService;
    @Mock private FitnessProfileRepository fitnessProfileRepository;
    @Mock private FitnessWeightLogRepository weightLogRepository;
    @Mock private FitnessMealLogRepository mealLogRepository;

    @InjectMocks
    private FitnessHealthService service;

    private FitnessProfile fullProfile(BiologicalSex sex, ActivityLevel level,
                                        Integer heightCm, Integer birthYear, BigDecimal targetWeight) {
        FitnessProfile p = new FitnessProfile();
        p.setId(1L);
        p.setBiologicalSex(sex);
        p.setActivityLevel(level);
        p.setHeightCm(heightCm);
        p.setBirthYear(birthYear);
        p.setTargetWeightKg(targetWeight);
        return p;
    }

    private FitnessWeightLog weightLog(double kg) {
        FitnessWeightLog w = new FitnessWeightLog();
        w.setWeightKg(BigDecimal.valueOf(kg));
        w.setLogDate(LocalDate.now());
        return w;
    }

    // ===== recalculateDailyCalorieTarget =====

    @Test
    void recalculateDailyCalorieTarget_missingProfileFields_returnsNull() {
        FitnessProfile p = new FitnessProfile(); // no height/age/sex/activity
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);

        assertThat(service.recalculateDailyCalorieTarget("alice")).isNull();
    }

    @Test
    void recalculateDailyCalorieTarget_noWeightLog_returnsNull() {
        FitnessProfile p = fullProfile(BiologicalSex.MALE, ActivityLevel.SEDENTARY, 175, 1990, null);
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        when(weightLogRepository.findFirstByFitnessProfileOrderByLogDateDesc(p)).thenReturn(Optional.empty());

        assertThat(service.recalculateDailyCalorieTarget("alice")).isNull();
    }

    @Test
    void recalculateDailyCalorieTarget_maleWithDeficit_returnsTdeeMinusFiveHundred() {
        // Male, 80kg, 175cm, age ~36, SEDENTARY, target 75kg (deficit)
        // BMR = 10*80 + 6.25*175 - 5*36 + 5 = 800 + 1093.75 - 180 + 5 = 1718.75
        // TDEE = 1718.75 * 1.2 = 2062.5  → deficit: 2062.5 - 500 = 1562/1563
        int birthYear = LocalDate.now().getYear() - 36;
        FitnessProfile p = fullProfile(BiologicalSex.MALE, ActivityLevel.SEDENTARY,
                175, birthYear, BigDecimal.valueOf(75.0));
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        when(weightLogRepository.findFirstByFitnessProfileOrderByLogDateDesc(p))
                .thenReturn(Optional.of(weightLog(80.0)));

        Integer result = service.recalculateDailyCalorieTarget("alice");

        assertThat(result).isNotNull();
        // weight > target by >1kg → deficit
        // rough check: TDEE minus 500
        double bmr = 10 * 80 + 6.25 * 175 - 5 * 36 + 5;
        double tdee = bmr * 1.2;
        assertThat(result).isEqualTo((int) Math.round(tdee - 500));
    }

    @Test
    void recalculateDailyCalorieTarget_femaleWithSurplus_returnsTdeePlusThreeHundred() {
        // Female, 60kg, 165cm, age 30, LIGHTLY_ACTIVE, target 65kg (surplus)
        int birthYear = LocalDate.now().getYear() - 30;
        FitnessProfile p = fullProfile(BiologicalSex.FEMALE, ActivityLevel.LIGHTLY_ACTIVE,
                165, birthYear, BigDecimal.valueOf(65.0));
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        when(weightLogRepository.findFirstByFitnessProfileOrderByLogDateDesc(p))
                .thenReturn(Optional.of(weightLog(60.0)));

        Integer result = service.recalculateDailyCalorieTarget("alice");

        double bmr = 10 * 60 + 6.25 * 165 - 5 * 30 - 161;
        double tdee = bmr * 1.375;
        assertThat(result).isEqualTo((int) Math.round(tdee + 300));
    }

    @Test
    void recalculateDailyCalorieTarget_maintainWeight_returnsTdee() {
        // Weight and target within 1kg of each other
        int birthYear = LocalDate.now().getYear() - 35;
        FitnessProfile p = fullProfile(BiologicalSex.MALE, ActivityLevel.MODERATELY_ACTIVE,
                180, birthYear, BigDecimal.valueOf(80.4));
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        when(weightLogRepository.findFirstByFitnessProfileOrderByLogDateDesc(p))
                .thenReturn(Optional.of(weightLog(80.0)));

        Integer result = service.recalculateDailyCalorieTarget("alice");

        double bmr = 10 * 80 + 6.25 * 180 - 5 * 35 + 5;
        double tdee = bmr * 1.55;
        assertThat(result).isEqualTo((int) Math.round(tdee));
    }

    @Test
    void recalculateDailyCalorieTarget_noTargetWeight_returnsTdee() {
        int birthYear = LocalDate.now().getYear() - 25;
        FitnessProfile p = fullProfile(BiologicalSex.MALE, ActivityLevel.VERY_ACTIVE,
                180, birthYear, null); // no target weight
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        when(weightLogRepository.findFirstByFitnessProfileOrderByLogDateDesc(p))
                .thenReturn(Optional.of(weightLog(75.0)));

        Integer result = service.recalculateDailyCalorieTarget("alice");

        double bmr = 10 * 75 + 6.25 * 180 - 5 * 25 + 5;
        double tdee = bmr * 1.725;
        assertThat(result).isEqualTo((int) Math.round(tdee));
    }

    // ===== logWeight =====

    @Test
    void logWeight_savesEntry() {
        FitnessProfile p = new FitnessProfile();
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        FitnessWeightLog saved = weightLog(82.5);
        saved.setId(1L);
        when(weightLogRepository.save(any())).thenReturn(saved);

        FitnessWeightLog result = service.logWeight("alice", BigDecimal.valueOf(82.5), "morning");

        verify(weightLogRepository).save(argThat(w ->
                ((FitnessWeightLog) w).getWeightKg().compareTo(BigDecimal.valueOf(82.5)) == 0));
        assertThat(result).isSameAs(saved);
    }

    // ===== getWeightHistory =====

    @Test
    void getWeightHistory_returnsMappedDTOs() {
        FitnessProfile p = new FitnessProfile();
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);

        FitnessWeightLog w1 = weightLog(80.0);
        w1.setNotes("note1");
        FitnessWeightLog w2 = weightLog(79.5);

        when(weightLogRepository.findByFitnessProfileOrderByLogDateDesc(p)).thenReturn(List.of(w1, w2));

        List<WeightLogDTO> result = service.getWeightHistory("alice");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getWeightKg()).isEqualByComparingTo(BigDecimal.valueOf(80.0));
        assertThat(result.get(0).getNotes()).isEqualTo("note1");
    }

    // ===== getHealthProfile =====

    @Test
    void getHealthProfile_returnsMappedDTO() {
        FitnessProfile p = fullProfile(BiologicalSex.FEMALE, ActivityLevel.LIGHTLY_ACTIVE,
                165, 1992, BigDecimal.valueOf(60.0));
        p.setTargetProteinG(120);
        p.setTargetCarbsG(200);
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);

        FitnessHealthProfileDTO dto = service.getHealthProfile("alice");

        assertThat(dto.getHeightCm()).isEqualTo(165);
        assertThat(dto.getBirthYear()).isEqualTo(1992);
        assertThat(dto.getBiologicalSex()).isEqualTo(BiologicalSex.FEMALE);
        assertThat(dto.getActivityLevel()).isEqualTo(ActivityLevel.LIGHTLY_ACTIVE);
        assertThat(dto.getTargetWeightKg()).isEqualByComparingTo(BigDecimal.valueOf(60.0));
        assertThat(dto.getTargetProteinG()).isEqualTo(120);
        assertThat(dto.getTargetCarbsG()).isEqualTo(200);
    }

    // ===== saveHealthProfile =====

    @Test
    void saveHealthProfile_updatesProfileAndSaves() {
        FitnessProfile p = new FitnessProfile();
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        when(fitnessProfileRepository.save(any())).thenReturn(p);

        FitnessHealthProfileDTO dto = new FitnessHealthProfileDTO(175, 1990, BiologicalSex.MALE,
                ActivityLevel.MODERATELY_ACTIVE, BigDecimal.valueOf(78.0), 130, 220);

        service.saveHealthProfile("alice", dto);

        assertThat(p.getHeightCm()).isEqualTo(175);
        assertThat(p.getBirthYear()).isEqualTo(1990);
        assertThat(p.getBiologicalSex()).isEqualTo(BiologicalSex.MALE);
        verify(fitnessProfileRepository).save(p);
    }

    // ===== getProteinTarget =====

    @Test
    void getProteinTarget_manuallySet_returnsManual() {
        FitnessProfile p = new FitnessProfile();
        p.setTargetProteinG(150);
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);

        assertThat(service.getProteinTarget("alice")).isEqualTo(150);
    }

    @Test
    void getProteinTarget_notSet_calculatesFromWeight() {
        FitnessProfile p = new FitnessProfile(); // no manual target
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        when(weightLogRepository.findFirstByFitnessProfileOrderByLogDateDesc(p))
                .thenReturn(Optional.of(weightLog(80.0)));

        // 80kg * 1.6 = 128g
        assertThat(service.getProteinTarget("alice")).isEqualTo(128);
    }

    @Test
    void getProteinTarget_noWeightLog_returnsNull() {
        FitnessProfile p = new FitnessProfile();
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        when(weightLogRepository.findFirstByFitnessProfileOrderByLogDateDesc(p))
                .thenReturn(Optional.empty());

        assertThat(service.getProteinTarget("alice")).isNull();
    }

    // ===== getCarbsTarget =====

    @Test
    void getCarbsTarget_manuallySet_returnsManual() {
        FitnessProfile p = new FitnessProfile();
        p.setTargetCarbsG(250);
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);

        assertThat(service.getCarbsTarget("alice")).isEqualTo(250);
    }

    @Test
    void getCarbsTarget_autoCalculated_returnsResult() {
        int birthYear = LocalDate.now().getYear() - 30;
        FitnessProfile p = fullProfile(BiologicalSex.MALE, ActivityLevel.SEDENTARY, 175, birthYear,
                BigDecimal.valueOf(75.0));
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);
        when(weightLogRepository.findFirstByFitnessProfileOrderByLogDateDesc(p))
                .thenReturn(Optional.of(weightLog(80.0)));

        Integer result = service.getCarbsTarget("alice");

        // Should return a non-null value computed from calorie target
        assertThat(result).isNotNull().isPositive();
    }

    @Test
    void getCarbsTarget_noCalorieTarget_returnsNull() {
        FitnessProfile p = new FitnessProfile(); // missing profile data
        when(fitnessService.getOrCreateFitnessProfile("alice")).thenReturn(p);

        assertThat(service.getCarbsTarget("alice")).isNull();
    }
}

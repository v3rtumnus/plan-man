package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.fitness.FitnessMealLogRepository;
import at.v3rtumnus.planman.dao.fitness.FitnessProfileRepository;
import at.v3rtumnus.planman.dao.fitness.FitnessWeightLogRepository;
import at.v3rtumnus.planman.dto.fitness.FitnessHealthProfileDTO;
import at.v3rtumnus.planman.dto.fitness.WeightLogDTO;
import at.v3rtumnus.planman.entity.fitness.ActivityLevel;
import at.v3rtumnus.planman.entity.fitness.BiologicalSex;
import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import at.v3rtumnus.planman.entity.fitness.FitnessWeightLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessHealthService {

    private final FitnessService fitnessService;
    private final FitnessProfileRepository fitnessProfileRepository;
    private final FitnessWeightLogRepository weightLogRepository;
    private final FitnessMealLogRepository mealLogRepository;

    @Transactional
    public FitnessWeightLog logWeight(String username, BigDecimal weightKg, String notes) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);

        FitnessWeightLog entry = new FitnessWeightLog();
        entry.setFitnessProfile(profile);
        entry.setLogDate(LocalDate.now());
        entry.setWeightKg(weightKg);
        entry.setNotes(notes);

        FitnessWeightLog saved = weightLogRepository.save(entry);
        log.info("Logged weight {}kg for user {}", weightKg, username);
        return saved;
    }

    /**
     * Calculates the daily calorie target based on the user's current weight, body data,
     * and target weight. Always computed live from the latest weight log.
     *
     * @return daily calorie target, or null if insufficient data
     */
    public Integer recalculateDailyCalorieTarget(String username) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);

        if (profile.getHeightCm() == null || profile.getBirthYear() == null
                || profile.getBiologicalSex() == null || profile.getActivityLevel() == null) {
            return null;
        }

        Optional<FitnessWeightLog> latestWeight = weightLogRepository
                .findFirstByFitnessProfileOrderByLogDateDesc(profile);
        if (latestWeight.isEmpty()) {
            return null;
        }

        double weight = latestWeight.get().getWeightKg().doubleValue();
        double height = profile.getHeightCm();
        int age = LocalDate.now().getYear() - profile.getBirthYear();

        double bmr = calculateBmr(weight, height, age, profile.getBiologicalSex());
        double tdee = bmr * getActivityFactor(profile.getActivityLevel());

        if (profile.getTargetWeightKg() == null) {
            return (int) Math.round(tdee);
        }

        double target = profile.getTargetWeightKg().doubleValue();
        double diff = target - weight;

        if (diff < -1.0) {
            return (int) Math.round(tdee - 500);
        } else if (diff > 1.0) {
            return (int) Math.round(tdee + 300);
        } else {
            return (int) Math.round(tdee);
        }
    }

    public List<WeightLogDTO> getWeightHistory(String username) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        return weightLogRepository.findByFitnessProfileOrderByLogDateDesc(profile).stream()
                .map(w -> new WeightLogDTO(w.getLogDate(), w.getWeightKg(), w.getNotes()))
                .collect(Collectors.toList());
    }

    public FitnessHealthProfileDTO getHealthProfile(String username) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        return new FitnessHealthProfileDTO(
                profile.getHeightCm(), profile.getBirthYear(), profile.getBiologicalSex(),
                profile.getActivityLevel(), profile.getTargetWeightKg(),
                profile.getTargetProteinG(), profile.getTargetCarbsG()
        );
    }

    @Transactional
    public void saveHealthProfile(String username, FitnessHealthProfileDTO dto) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        profile.setHeightCm(dto.getHeightCm());
        profile.setBirthYear(dto.getBirthYear());
        profile.setBiologicalSex(dto.getBiologicalSex());
        profile.setActivityLevel(dto.getActivityLevel());
        profile.setTargetWeightKg(dto.getTargetWeightKg());
        profile.setTargetProteinG(dto.getTargetProteinG());
        profile.setTargetCarbsG(dto.getTargetCarbsG());
        fitnessProfileRepository.save(profile);
        log.info("Saved health profile for user {}", username);
    }

    /**
     * Returns the auto-calculated protein target in grams.
     * Uses 1.6g per kg of current body weight as the default.
     */
    public Integer getProteinTarget(String username) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        if (profile.getTargetProteinG() != null) {
            return profile.getTargetProteinG();
        }
        Optional<FitnessWeightLog> latestWeight = weightLogRepository
                .findFirstByFitnessProfileOrderByLogDateDesc(profile);
        return latestWeight.map(w -> (int) Math.round(w.getWeightKg().doubleValue() * 1.6)).orElse(null);
    }

    /**
     * Returns the auto-calculated carbohydrate target in grams.
     * Computed as: (calorie_target - protein*4 - fat*9) / 4, where fat = 25% of calories.
     */
    public Integer getCarbsTarget(String username) {
        FitnessProfile profile = fitnessService.getOrCreateFitnessProfile(username);
        if (profile.getTargetCarbsG() != null) {
            return profile.getTargetCarbsG();
        }
        Integer calorieTarget = recalculateDailyCalorieTarget(username);
        Integer proteinTarget = getProteinTarget(username);
        if (calorieTarget == null || proteinTarget == null) {
            return null;
        }
        double fatCalories = calorieTarget * 0.25;
        double proteinCalories = proteinTarget * 4.0;
        double carbCalories = calorieTarget - proteinCalories - fatCalories;
        return (int) Math.round(carbCalories / 4.0);
    }

    /**
     * Returns the auto-calculated fat target in grams.
     * Computed as: 25% of calorie target / 9 kcal per gram.
     */
    public Integer getFatTarget(String username) {
        Integer calorieTarget = recalculateDailyCalorieTarget(username);
        if (calorieTarget == null) return null;
        double fatCalories = calorieTarget * 0.25;
        return (int) Math.round(fatCalories / 9.0);
    }

    private double calculateBmr(double weight, double height, int age, BiologicalSex sex) {
        // Mifflin-St Jeor formula
        double base = 10 * weight + 6.25 * height - 5 * age;
        return sex == BiologicalSex.MALE ? base + 5 : base - 161;
    }

    private double getActivityFactor(ActivityLevel level) {
        return switch (level) {
            case SEDENTARY -> 1.2;
            case LIGHTLY_ACTIVE -> 1.375;
            case MODERATELY_ACTIVE -> 1.55;
            case VERY_ACTIVE -> 1.725;
        };
    }
}

package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dao.fitness.FitnessMealLogRepository;
import at.v3rtumnus.planman.dao.fitness.FitnessProfileRepository;
import at.v3rtumnus.planman.dao.fitness.FitnessWeightLogRepository;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.fitness.FitnessProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessService {

    private final UserProfileRepository userProfileRepository;
    private final FitnessProfileRepository fitnessProfileRepository;
    private final FitnessWeightLogRepository weightLogRepository;
    private final FitnessMealLogRepository mealLogRepository;

    @Transactional
    public FitnessProfile getOrCreateFitnessProfile(String username) {
        return fitnessProfileRepository.findByUserProfileUsername(username)
                .orElseGet(() -> {
                    UserProfile user = userProfileRepository.findByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found: " + username));
                    FitnessProfile profile = new FitnessProfile();
                    profile.setUserProfile(user);
                    profile.setCreatedAt(LocalDateTime.now());
                    log.info("Creating new fitness profile for user {}", username);
                    return fitnessProfileRepository.save(profile);
                });
    }

    @Transactional
    public void resetFitnessData(String username) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        log.info("Resetting fitness data for user {}", username);
        weightLogRepository.deleteByFitnessProfile(profile);
        mealLogRepository.deleteByFitnessProfile(profile);
        profile.setHeightCm(null);
        profile.setBirthYear(null);
        profile.setBiologicalSex(null);
        profile.setActivityLevel(null);
        profile.setTargetWeightKg(null);
        profile.setTargetProteinG(null);
        profile.setTargetCarbsG(null);
        fitnessProfileRepository.save(profile);
    }
}

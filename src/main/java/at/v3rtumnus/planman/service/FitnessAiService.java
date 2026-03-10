package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dao.fitness.*;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.fitness.*;
import at.v3rtumnus.planman.exception.FitnessAiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessAiService {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserProfileRepository userProfileRepository;
    private final FitnessProfileRepository fitnessProfileRepository;
    private final FitnessAssessmentAnswerRepository assessmentAnswerRepository;
    private final FitnessExerciseRepository exerciseRepository;
    private final FitnessPlanRepository fitnessPlanRepository;
    private final FitnessPlanSessionRepository fitnessPlanSessionRepository;
    private final FitnessPlanExerciseRepository fitnessPlanExerciseRepository;
    private final FitnessSessionLogRepository sessionLogRepository;
    private final FitnessSessionExerciseLogRepository sessionExerciseLogRepository;

    @Value("${fitness.ai.timeout-seconds:30}")
    private int aiTimeoutSeconds;

    @Value("${fitness.ai.plan-model:gpt-4.1-mini}")
    private String planModel;

    private FitnessProfile getOrCreateProfile(String username) {
        return fitnessProfileRepository.findByUserProfileUsername(username)
                .orElseGet(() -> {
                    UserProfile user = userProfileRepository.findByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found: " + username));
                    FitnessProfile profile = new FitnessProfile();
                    profile.setUserProfile(user);
                    profile.setAssessmentCompleted(false);
                    profile.setCreatedAt(LocalDateTime.now());
                    return fitnessProfileRepository.save(profile);
                });
    }

    @Transactional
    public FitnessPlan generateInitialPlan(String username) {
        return doGeneratePlan(username, PlanGenerationReason.INITIAL);
    }

    @Transactional
    public FitnessPlan regeneratePlan(String username) {
        return doGeneratePlan(username, PlanGenerationReason.MANUAL);
    }

    private FitnessPlan doGeneratePlan(String username, PlanGenerationReason reason) {
        FitnessProfile profile = getOrCreateProfile(username);
        List<FitnessAssessmentAnswer> answers = assessmentAnswerRepository.findByFitnessProfile(profile);
        List<FitnessExercise> exercises = exerciseRepository.findAll();

        String prompt = buildInitialPlanPrompt(answers, exercises);
        log.info("Generating {} fitness plan for user {}", reason, username);

        String response = callAiWithTimeout(prompt, "KI-Plan-Generierung fehlgeschlagen", planModel);
        AiPlanResponse aiPlan = parseAiPlanResponse(response);

        return persistPlan(profile, aiPlan, reason, exercises);
    }

    @Transactional
    public FitnessPlan evolvePlan(String username) {
        FitnessProfile profile = getOrCreateProfile(username);
        FitnessPlan activePlan = fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)
                .orElseThrow(() -> new FitnessAiException("No active plan found for evolution"));

        // Find the most recently completed but not yet analyzed week
        int completedWeek = findCompletedUnananalyzedWeek(activePlan);
        if (completedWeek < 0) {
            log.warn("No completed unanalyzed week found for user {}", username);
            return activePlan;
        }

        List<FitnessPlanSession> weekSessions = fitnessPlanSessionRepository
                .findByFitnessPlanAndWeekNumber(activePlan, completedWeek);
        List<FitnessSessionLog> weekLogs = weekSessions.stream()
                .map(s -> sessionLogRepository.findTopByPlanSessionOrderByIdDesc(s))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        List<FitnessExercise> exercises = exerciseRepository.findAll();
        String exerciseRefreshInstruction = detectExerciseRefreshInstruction(profile, activePlan);
        List<FitnessSessionLog> teamSportLogs = sessionLogRepository
                .findByFitnessProfileAndPlanSessionIsNullAndSessionTypeAndAiAnalyzedFalse(profile, SessionType.TEAM_SPORT);
        String prompt = buildEvolutionPrompt(activePlan, weekLogs, weekSessions, exercises, exerciseRefreshInstruction, teamSportLogs);

        log.info("Evolving fitness plan for user {}, analyzing week {}", username, completedWeek);
        String response = callAiWithTimeout(prompt, "KI-Plan-Evolution fehlgeschlagen", planModel);
        AiPlanResponse aiPlan = parseAiPlanResponse(response);

        // Persist new exercises from refresh if any
        if (aiPlan.newExercises != null && !aiPlan.newExercises.isEmpty()) {
            persistNewExercisesFromRefresh(aiPlan.newExercises);
        }

        // Reload exercises after potential additions
        exercises = exerciseRepository.findAll();

        // Deactivate old plan
        activePlan.setActive(false);
        fitnessPlanRepository.save(activePlan);

        // Mark analyzed logs (planned sessions + team sport)
        weekLogs.forEach(l -> {
            l.setAiAnalyzed(true);
            sessionLogRepository.save(l);
        });
        teamSportLogs.forEach(l -> {
            l.setAiAnalyzed(true);
            sessionLogRepository.save(l);
        });

        return persistPlan(profile, aiPlan, PlanGenerationReason.EVOLUTION, exercises);
    }

    private int findCompletedUnananalyzedWeek(FitnessPlan plan) {
        List<FitnessPlanSession> allSessions = fitnessPlanSessionRepository
                .findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(plan);

        Map<Integer, List<FitnessPlanSession>> byWeek = allSessions.stream()
                .collect(Collectors.groupingBy(FitnessPlanSession::getWeekNumber));

        for (Map.Entry<Integer, List<FitnessPlanSession>> entry : new TreeMap<>(byWeek).entrySet()) {
            List<FitnessPlanSession> sessions = entry.getValue();
            boolean allLogged = sessions.stream()
                    .allMatch(s -> sessionLogRepository.findTopByPlanSessionOrderByIdDesc(s).isPresent());
            boolean anyUnanalyzed = sessions.stream()
                    .map(s -> sessionLogRepository.findTopByPlanSessionOrderByIdDesc(s))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .anyMatch(l -> !l.isAiAnalyzed());

            if (allLogged && anyUnanalyzed) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private String detectExerciseRefreshInstruction(FitnessProfile profile, FitnessPlan currentPlan) {
        List<FitnessPlan> recentPlans = fitnessPlanRepository
                .findByFitnessProfileOrderByVersionDesc(profile);

        if (recentPlans.size() < 2) {
            return "";
        }

        // Check last 2 plans' bodyweight session ratings
        FitnessPlan prev1 = recentPlans.get(0); // most recent (current)
        FitnessPlan prev2 = recentPlans.get(1);

        OptionalDouble avg1 = getAverageBodyweightRating(prev1);
        OptionalDouble avg2 = getAverageBodyweightRating(prev2);

        if (avg1.isEmpty() || avg2.isEmpty()) {
            return "";
        }

        boolean bothTooEasy = avg1.getAsDouble() <= 2.0 && avg2.getAsDouble() <= 2.0;
        boolean bothTooHard = avg1.getAsDouble() >= 4.0 && avg2.getAsDouble() >= 4.0;

        if (bothTooEasy) {
            return "Ersetze die Körpergewichts-Übungen komplett durch schwerere Übungen aus der Bibliothek. Du kannst auch neue Übungen vorschlagen die NICHT in der Bibliothek sind.";
        } else if (bothTooHard) {
            return "Ersetze die Körpergewichts-Übungen komplett durch leichtere Übungen aus der Bibliothek. Du kannst auch neue Übungen vorschlagen die NICHT in der Bibliothek sind.";
        }
        return "";
    }

    private OptionalDouble getAverageBodyweightRating(FitnessPlan plan) {
        List<FitnessPlanSession> sessions = fitnessPlanSessionRepository
                .findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(plan);
        return sessions.stream()
                .filter(s -> s.getSessionType() == SessionType.BODYWEIGHT)
                .map(s -> sessionLogRepository.findTopByPlanSessionOrderByIdDesc(s))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(l -> l.getStatus() == SessionStatus.COMPLETED && l.getDifficultyRating() != null)
                .mapToInt(FitnessSessionLog::getDifficultyRating)
                .average();
    }

    private void persistNewExercisesFromRefresh(List<AiNewExercise> newExercises) {
        for (AiNewExercise ae : newExercises) {
            try {
                FitnessExercise exercise = new FitnessExercise();
                exercise.setName(ae.name);
                exercise.setDescription(ae.description);
                exercise.setCategory(ExerciseCategory.valueOf(ae.category));
                exercise.setTrackingType(ExerciseTrackingType.valueOf(ae.trackingType));
                exercise.setEquipment(Equipment.valueOf(ae.equipment));
                exercise.setDifficulty(ae.difficulty);
                exerciseRepository.save(exercise);
                log.info("Persisted new exercise from AI refresh: {}", ae.name);
            } catch (Exception e) {
                log.warn("Failed to persist new exercise '{}': {}", ae.name, e.getMessage());
            }
        }
    }

    private FitnessPlan persistPlan(FitnessProfile profile, AiPlanResponse aiPlan,
                                    PlanGenerationReason reason, List<FitnessExercise> exercises) {
        // Determine next version
        List<FitnessPlan> existingPlans = fitnessPlanRepository.findByFitnessProfileOrderByVersionDesc(profile);
        int nextVersion = existingPlans.isEmpty() ? 1 : existingPlans.get(0).getVersion() + 1;

        // Deactivate existing plans if creating initial
        if (reason == PlanGenerationReason.INITIAL) {
            existingPlans.forEach(p -> {
                p.setActive(false);
                fitnessPlanRepository.save(p);
            });
        }

        FitnessPlan plan = new FitnessPlan();
        plan.setFitnessProfile(profile);
        plan.setVersion(nextVersion);
        plan.setGeneratedAt(LocalDateTime.now());
        plan.setActive(true);
        plan.setGenerationReason(reason);
        plan.setAiNotes(aiPlan.aiNotes);
        plan.setExerciseRefresh(aiPlan.exerciseRefresh);
        FitnessPlan savedPlan = fitnessPlanRepository.save(plan);

        // Update profile's current plan reference
        profile.setCurrentPlanId(savedPlan.getId());
        fitnessProfileRepository.save(profile);

        // Build exercise lookup map by ID
        Map<Long, FitnessExercise> exerciseMap = exercises.stream()
                .collect(Collectors.toMap(FitnessExercise::getId, e -> e));
        // Also build lookup by name for new exercises
        Map<String, FitnessExercise> exerciseByName = exercises.stream()
                .collect(Collectors.toMap(FitnessExercise::getName, e -> e, (a, b) -> a));

        if (aiPlan.sessions != null) {
            for (AiSession aiSession : aiPlan.sessions) {
                FitnessPlanSession session = new FitnessPlanSession();
                session.setFitnessPlan(savedPlan);
                session.setSessionNumber(aiSession.sessionNumber);
                session.setWeekNumber(aiSession.week);
                session.setSessionType(parseSessionType(aiSession.sessionType));
                session.setEstimatedDurationMinutes(aiSession.estimatedDurationMinutes);
                session.setDescription(aiSession.description);
                FitnessPlanSession savedSession = fitnessPlanSessionRepository.save(session);

                if (aiSession.exercises != null) {
                    for (AiExercise aiEx : aiSession.exercises) {
                        FitnessExercise exercise = resolveExercise(aiEx, exerciseMap, exerciseByName);
                        if (exercise == null) {
                            log.warn("Exercise not found for id={}, name={} — skipping", aiEx.exerciseId, aiEx.exerciseName);
                            continue;
                        }
                        FitnessPlanExercise planEx = new FitnessPlanExercise();
                        planEx.setPlanSession(savedSession);
                        planEx.setExercise(exercise);
                        planEx.setOrderIndex(aiEx.orderIndex);
                        planEx.setTargetSets(aiEx.targetSets);
                        planEx.setTargetReps(aiEx.targetReps);
                        planEx.setTargetDurationSeconds(aiEx.targetDurationSeconds);
                        planEx.setTargetDistanceMeters(aiEx.targetDistanceMeters);
                        planEx.setTargetDurationRunSeconds(aiEx.targetDurationRunSeconds);
                        planEx.setRestSeconds(aiEx.restSeconds != null ? aiEx.restSeconds : 60);
                        planEx.setNotes(aiEx.notes);
                        fitnessPlanExerciseRepository.save(planEx);
                    }
                }
            }
        }

        log.info("Persisted plan version {} for user {} with {} sessions",
                nextVersion, profile.getUserProfile().getUsername(),
                aiPlan.sessions != null ? aiPlan.sessions.size() : 0);
        return savedPlan;
    }

    private FitnessExercise resolveExercise(AiExercise aiEx,
                                             Map<Long, FitnessExercise> byId,
                                             Map<String, FitnessExercise> byName) {
        if (aiEx.exerciseId != null) {
            return byId.get(aiEx.exerciseId);
        }
        if (aiEx.exerciseName != null) {
            return byName.get(aiEx.exerciseName);
        }
        return null;
    }

    private SessionType parseSessionType(String value) {
        if (value == null) return SessionType.BODYWEIGHT;
        try {
            return SessionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown session type '{}', defaulting to BODYWEIGHT", value);
            return SessionType.BODYWEIGHT;
        }
    }

    private String callAiWithTimeout(String prompt, String errorMessage) {
        return callAiWithTimeout(prompt, errorMessage, null);
    }

    private String callAiWithTimeout(String prompt, String errorMessage, String modelOverride) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            ChatClient.ChatClientRequestSpec spec = ChatClient.builder(chatModel).build()
                    .prompt()
                    .user(prompt);
            if (modelOverride != null) {
                spec = spec.options(OpenAiChatOptions.builder().model(modelOverride).build());
            }
            return spec.call().content();
        });
        try {
            String response = future.get(aiTimeoutSeconds, TimeUnit.SECONDS);
            if (response == null || response.isBlank()) {
                throw new FitnessAiException(errorMessage + ": empty response");
            }
            return response;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new FitnessAiException(errorMessage + ": timeout after " + aiTimeoutSeconds + "s", e);
        } catch (FitnessAiException e) {
            throw e;
        } catch (Exception e) {
            throw new FitnessAiException(errorMessage, e);
        }
    }

    private AiPlanResponse parseAiPlanResponse(String response) {
        String json = extractJson(response);
        try {
            return objectMapper.readValue(json, AiPlanResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse AI plan response: {}", json, e);
            throw new FitnessAiException("Failed to parse AI response", e);
        }
    }

    /**
     * Strips markdown code fences if the AI wrapped its JSON in ```json ... ```.
     */
    private String extractJson(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String buildInitialPlanPrompt(List<FitnessAssessmentAnswer> answers, List<FitnessExercise> exercises) {
        Map<String, String> answerMap = answers.stream()
                .collect(Collectors.toMap(FitnessAssessmentAnswer::getQuestionKey,
                        FitnessAssessmentAnswer::getAnswerValue, (a, b) -> a));

        String exerciseJson = serializeExerciseLibrary(exercises);

        int maxRunMin     = parseIntSafe(answerMap.getOrDefault("max_run_minutes", "0"));
        int maxPushups    = parseIntSafe(answerMap.getOrDefault("max_pushups", "0"));
        int maxPlankSec   = parseIntSafe(answerMap.getOrDefault("max_plank_seconds", "0"));
        int maxSquats     = parseIntSafe(answerMap.getOrDefault("max_squats", "0"));
        int maxSitups     = parseIntSafe(answerMap.getOrDefault("max_situps", "0"));

        int startRunMin   = Math.max(1, (int) Math.round(maxRunMin   * 0.6));
        int startPushups  = Math.max(1, (int) Math.round(maxPushups  * 0.6));
        int startPlankSec = Math.max(10, (int) Math.round(maxPlankSec * 0.6));
        int startSquats   = Math.max(1, (int) Math.round(maxSquats   * 0.6));
        int startSitups   = Math.max(1, (int) Math.round(maxSitups   * 0.6));

        return """
                Du bist ein Fitness-Coach. Erstelle einen 4-Wochen-Trainingsplan für eine Person mit folgendem Profil:
                - Verletzungen/Einschränkungen: %s
                - Schwerpunkt: %s
                - Trainingstage pro Woche: %s
                - Laufen möglich: %s
                - Liegestütze am Stück (Maximum): %d → Startziel Woche 1: %d
                - Laufen ohne Pause (Maximum): %d min → Startziel Woche 1: %d min
                - Plank (Maximum): %d s → Startziel Woche 1: %d s
                - Kniebeugen am Stück (Maximum): %d → Startziel Woche 1: %d
                - Sit-ups/Crunches am Stück (Maximum): %d → Startziel Woche 1: %d
                - Zusätzliche Hinweise: %s

                Verfügbare Übungen (JSON-Array): %s

                Regeln:
                - Jede Einheit dauert ca. 30 Minuten
                - Wechsle zwischen Lauf- und Körpergewichts-Einheiten
                - Jeder Trainingstag hat genau EINEN Typ: entweder "RUNNING" oder "BODYWEIGHT" — niemals beides kombiniert
                - Jede Einheit: Warm-Up + Hauptteil + Abkühlen
                - Steigere Schwierigkeit progressiv von Woche zu Woche
                - WICHTIG: Verwende in Woche 1 exakt die oben angegebenen Startziele — leite keine anderen Werte ab und erfinde keine
                - Lauf-Einheiten in Woche 1: Wenn das Startziel Laufen ≤ 3 min ist, nutze Geh-Lauf-Intervalle; ansonsten nutze einen einzelnen kontinuierlichen Lauf von genau %d min
                - Überschreite in keiner Woche-1-Einheit das Startziel Laufen von %d min pro Laufintervall

                Antworte ausschließlich als gültiges JSON (kein Markdown, kein Text davor/danach):
                {
                  "ai_notes": "...",
                  "exercise_refresh": false,
                  "sessions": [
                    {
                      "week": 1,
                      "session_number": 1,
                      "session_type": "RUNNING",
                      "description": "...",
                      "estimated_duration_minutes": 30,
                      "exercises": [
                        {
                          "exercise_id": 1,
                          "exercise_name": null,
                          "order_index": 1,
                          "target_sets": null,
                          "target_reps": null,
                          "target_duration_seconds": null,
                          "target_distance_meters": 1000,
                          "target_duration_run_seconds": 1800,
                          "rest_seconds": 60,
                          "notes": "..."
                        }
                      ]
                    }
                  ]
                }
                """.formatted(
                answerMap.getOrDefault("injuries", "keine"),
                answerMap.getOrDefault("focus_area", "GLEICHMAESSIG"),
                answerMap.getOrDefault("training_days", "3"),
                answerMap.getOrDefault("can_run_outside", "JA"),
                maxPushups, startPushups,
                maxRunMin, startRunMin,
                maxPlankSec, startPlankSec,
                maxSquats, startSquats,
                maxSitups, startSitups,
                answerMap.getOrDefault("additional_notes", "keine"),
                exerciseJson,
                startRunMin,
                startRunMin
        );
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String buildEvolutionPrompt(FitnessPlan currentPlan, List<FitnessSessionLog> weekLogs,
                                         List<FitnessPlanSession> weekSessions,
                                         List<FitnessExercise> exercises, String exerciseRefreshInstruction,
                                         List<FitnessSessionLog> teamSportLogs) {
        StringBuilder sessionSummary = new StringBuilder();
        for (FitnessSessionLog log : weekLogs) {
            sessionSummary.append("- Session %d (%s): Status=%s"
                    .formatted(log.getPlanSession() != null ? log.getPlanSession().getSessionNumber() : 0,
                            log.getSessionType(), log.getStatus()));
            if (log.getStatus() == SessionStatus.SKIPPED) {
                sessionSummary.append(", Grund=%s".formatted(log.getSkipReason()));
                if (log.getSkipNotes() != null) {
                    sessionSummary.append(" (%s)".formatted(log.getSkipNotes()));
                }
            } else {
                if (log.getDifficultyRating() != null) {
                    sessionSummary.append(", Schwierigkeit=%d/5".formatted(log.getDifficultyRating()));
                }
                if (log.getFeedbackText() != null) {
                    sessionSummary.append(", Feedback: %s".formatted(log.getFeedbackText()));
                }
            }
            sessionSummary.append("\n");
        }

        StringBuilder teamSportSection = new StringBuilder();
        if (teamSportLogs != null && !teamSportLogs.isEmpty()) {
            teamSportSection.append("\nExterne Aktivitäten außerhalb des Plans:\n");
            for (FitnessSessionLog ts : teamSportLogs) {
                teamSportSection.append("- %s: TEAM_SPORT".formatted(ts.getLogDate()));
                if (ts.getFeedbackText() != null) teamSportSection.append(" (%s)".formatted(ts.getFeedbackText()));
                if (ts.getActualDurationMinutes() != null) teamSportSection.append(", %d min".formatted(ts.getActualDurationMinutes()));
                if (ts.getExternalCaloriesBurned() != null) teamSportSection.append(", ~%d kcal".formatted(ts.getExternalCaloriesBurned()));
                teamSportSection.append("\n");
            }
        }

        String exerciseJson = serializeExerciseLibrary(exercises);
        String refreshSection = exerciseRefreshInstruction.isBlank()
                ? "Keine Anpassung der Übungsauswahl nötig — nur Parameter optimieren."
                : exerciseRefreshInstruction;

        return """
                Du bist ein Fitness-Coach. Analysiere die letzte abgeschlossene Trainingswoche und erstelle einen angepassten 4-Wochen-Plan.

                Sessions der letzten Woche:
                %s%s
                Verfügbare Übungen: %s

                Regeln:
                - Jeder Trainingstag bleibt entweder "RUNNING" oder "BODYWEIGHT"
                - Übersprungene Sessions wegen SICK: Schwierigkeit leicht reduzieren oder halten
                - Übersprungene Sessions wegen NO_TIME: Schwierigkeit halten
                - Übersprungene Sessions wegen OTHER_SPORT: Erholung berücksichtigen, Schwierigkeit halten oder leicht steigern
                - TEAM_SPORT-Aktivitäten: Als intensive Einheit werten, bei Bedarf Erholung am Folgetag einplanen
                - Bewertungen ≤ 2/5: Schwierigkeit reduzieren
                - Bewertungen ≥ 4/5: Schwierigkeit steigern
                - Übungs-Refresh: %s
                - Neue Übungen (nicht in Bibliothek) als "new_exercises" Liste angeben

                Antworte ausschließlich als gültiges JSON (kein Markdown):
                {
                  "ai_notes": "...",
                  "exercise_refresh": false,
                  "new_exercises": [],
                  "sessions": [ ... ]
                }
                """.formatted(sessionSummary, teamSportSection, exerciseJson, refreshSection);
    }

    private String serializeExerciseLibrary(List<FitnessExercise> exercises) {
        try {
            List<Map<String, Object>> list = exercises.stream()
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", e.getId());
                        m.put("name", e.getName());
                        m.put("category", e.getCategory());
                        m.put("tracking_type", e.getTrackingType());
                        m.put("equipment", e.getEquipment());
                        m.put("difficulty", e.getDifficulty());
                        return m;
                    })
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("Failed to serialize exercise library", e);
            return "[]";
        }
    }

    // ===== Inner classes for AI JSON response parsing =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiPlanResponse {
        @JsonProperty("ai_notes") String aiNotes;
        @JsonProperty("exercise_refresh") boolean exerciseRefresh;
        @JsonProperty("sessions") List<AiSession> sessions;
        @JsonProperty("new_exercises") List<AiNewExercise> newExercises;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiSession {
        @JsonProperty("week") int week;
        @JsonProperty("session_number") int sessionNumber;
        @JsonProperty("session_type") String sessionType;
        @JsonProperty("description") String description;
        @JsonProperty("estimated_duration_minutes") Integer estimatedDurationMinutes;
        @JsonProperty("exercises") List<AiExercise> exercises;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiExercise {
        @JsonProperty("exercise_id") Long exerciseId;
        @JsonProperty("exercise_name") String exerciseName;
        @JsonProperty("order_index") int orderIndex;
        @JsonProperty("target_sets") Integer targetSets;
        @JsonProperty("target_reps") Integer targetReps;
        @JsonProperty("target_duration_seconds") Integer targetDurationSeconds;
        @JsonProperty("target_distance_meters") Integer targetDistanceMeters;
        @JsonProperty("target_duration_run_seconds") Integer targetDurationRunSeconds;
        @JsonProperty("rest_seconds") Integer restSeconds;
        @JsonProperty("notes") String notes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiNewExercise {
        @JsonProperty("name") String name;
        @JsonProperty("description") String description;
        @JsonProperty("category") String category;
        @JsonProperty("tracking_type") String trackingType;
        @JsonProperty("equipment") String equipment;
        @JsonProperty("difficulty") int difficulty;
        @JsonProperty("image_search_hint") String imageSearchHint;
    }
}

package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dao.fitness.*;
import at.v3rtumnus.planman.dto.fitness.*;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.fitness.*;
import at.v3rtumnus.planman.exception.FitnessAiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessService {

    private final UserProfileRepository userProfileRepository;
    private final FitnessProfileRepository fitnessProfileRepository;
    private final FitnessAssessmentAnswerRepository assessmentAnswerRepository;
    private final FitnessPlanRepository fitnessPlanRepository;
    private final FitnessPlanSessionRepository fitnessPlanSessionRepository;
    private final FitnessPlanExerciseRepository fitnessPlanExerciseRepository;
    private final FitnessExerciseRepository exerciseRepository;
    private final FitnessSessionLogRepository sessionLogRepository;
    private final FitnessSessionExerciseLogRepository sessionExerciseLogRepository;
    private final FitnessWeightLogRepository weightLogRepository;
    private final FitnessMealLogRepository mealLogRepository;
    private final FitnessAiService fitnessAiService;

    @Transactional
    public FitnessProfile getOrCreateFitnessProfile(String username) {
        return fitnessProfileRepository.findByUserProfileUsername(username)
                .orElseGet(() -> {
                    UserProfile user = userProfileRepository.findByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found: " + username));
                    FitnessProfile profile = new FitnessProfile();
                    profile.setUserProfile(user);
                    profile.setAssessmentCompleted(false);
                    profile.setCreatedAt(LocalDateTime.now());
                    log.info("Creating new fitness profile for user {}", username);
                    return fitnessProfileRepository.save(profile);
                });
    }

    @Transactional
    public void saveAssessmentAnswers(String username, List<AssessmentAnswerDTO> answers) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        assessmentAnswerRepository.deleteByFitnessProfile(profile);

        List<FitnessAssessmentAnswer> entities = answers.stream()
                .map(dto -> {
                    FitnessAssessmentAnswer answer = new FitnessAssessmentAnswer();
                    answer.setFitnessProfile(profile);
                    answer.setQuestionKey(dto.getQuestionKey());
                    answer.setAnswerValue(dto.getAnswerValue());
                    return answer;
                })
                .collect(Collectors.toList());

        assessmentAnswerRepository.saveAll(entities);

        profile.setAssessmentCompleted(true);
        fitnessProfileRepository.save(profile);
        log.info("Saved {} assessment answers for user {}", answers.size(), username);
    }

    public FitnessPlanDTO getActivePlan(String username) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        Optional<FitnessPlan> planOpt = fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile);
        if (planOpt.isEmpty()) {
            return null;
        }
        FitnessPlanDTO planDTO = toPlanDTO(planOpt.get());
        if (planDTO != null && planDTO.getSessions() == null) {
            planDTO.setSessions(new ArrayList<>());
        }
        return planDTO;
    }

    @Transactional
    public FitnessSessionLog saveSessionLog(String username, SessionLogDTO dto) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);

        FitnessPlanSession planSession = null;
        if (dto.getPlanSessionId() != null) {
            planSession = fitnessPlanSessionRepository.findById(dto.getPlanSessionId())
                    .orElse(null);
        }

        FitnessSessionLog log = new FitnessSessionLog();
        log.setFitnessProfile(profile);
        log.setPlanSession(planSession);
        log.setLogDate(dto.getLogDate() != null ? dto.getLogDate() : LocalDate.now());
        log.setSessionType(dto.getSessionType());
        log.setStatus(SessionStatus.COMPLETED);
        log.setActualDurationMinutes(dto.getActualDurationMinutes());
        log.setDifficultyRating(dto.getDifficultyRating());
        log.setFeedbackText(dto.getFeedbackText());
        log.setExternalCaloriesBurned(dto.getExternalCaloriesBurned());
        log.setAiAnalyzed(false);
        FitnessSessionLog savedLog = sessionLogRepository.save(log);

        if (dto.getExercises() != null) {
            List<FitnessSessionExerciseLog> exerciseLogs = dto.getExercises().stream()
                    .map(exDto -> {
                        FitnessExercise exercise = new FitnessExercise();
                        exercise.setId(exDto.getExerciseId());
                        FitnessSessionExerciseLog exLog = new FitnessSessionExerciseLog();
                        exLog.setSessionLog(savedLog);
                        exLog.setExercise(exercise);
                        exLog.setOrderIndex(exDto.getOrderIndex());
                        exLog.setSetNumber(exDto.getSetNumber());
                        exLog.setRepsDone(exDto.getRepsDone());
                        exLog.setDurationSeconds(exDto.getDurationSeconds());
                        exLog.setDistanceMeters(exDto.getDistanceMeters());
                        exLog.setDurationRunSeconds(exDto.getDurationRunSeconds());
                        exLog.setNotes(exDto.getNotes());
                        return exLog;
                    })
                    .collect(Collectors.toList());
            sessionExerciseLogRepository.saveAll(exerciseLogs);
        }

        checkAndTriggerEvolution(username, profile, planSession);
        return savedLog;
    }

    @Transactional
    public FitnessSessionLog markSessionComplete(String username, Long planSessionId, Integer difficultyRating, String notes) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        FitnessPlanSession planSession = fitnessPlanSessionRepository.findById(planSessionId)
                .orElseThrow(() -> new RuntimeException("Plan session not found: " + planSessionId));

        FitnessSessionLog log = new FitnessSessionLog();
        log.setFitnessProfile(profile);
        log.setPlanSession(planSession);
        log.setLogDate(LocalDate.now());
        log.setSessionType(planSession.getSessionType());
        log.setStatus(SessionStatus.COMPLETED);
        log.setDifficultyRating(difficultyRating);
        log.setFeedbackText(notes);
        log.setAiAnalyzed(false);
        FitnessSessionLog savedLog = sessionLogRepository.save(log);

        checkAndTriggerEvolution(username, profile, planSession);
        return savedLog;
    }

    @Transactional
    public FitnessSessionLog skipSession(String username, Long planSessionId, SkipReason reason, String notes) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        FitnessPlanSession planSession = fitnessPlanSessionRepository.findById(planSessionId)
                .orElseThrow(() -> new RuntimeException("Plan session not found: " + planSessionId));

        FitnessSessionLog log = new FitnessSessionLog();
        log.setFitnessProfile(profile);
        log.setPlanSession(planSession);
        log.setLogDate(LocalDate.now());
        log.setSessionType(planSession.getSessionType());
        log.setStatus(SessionStatus.SKIPPED);
        log.setSkipReason(reason);
        log.setSkipNotes(notes);
        log.setAiAnalyzed(false);
        FitnessSessionLog savedLog = sessionLogRepository.save(log);

        checkAndTriggerEvolution(username, profile, planSession);
        return savedLog;
    }

    public List<FitnessSessionLog> getExternalActivitiesForDate(String username, LocalDate date) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        return sessionLogRepository.findByFitnessProfileAndLogDateAndSessionType(profile, date, SessionType.TEAM_SPORT);
    }

    public int getExternalCaloriesForDate(String username, LocalDate date) {
        return getExternalActivitiesForDate(username, date).stream()
                .filter(l -> l.getExternalCaloriesBurned() != null)
                .mapToInt(FitnessSessionLog::getExternalCaloriesBurned)
                .sum();
    }

    public List<FitnessExercise> getAllExercises() {
        return exerciseRepository.findAll();
    }

    public List<FitnessSessionLog> getSessionHistory(String username, int limit) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        List<FitnessSessionLog> all = sessionLogRepository.findByFitnessProfileOrderByLogDateDesc(profile);
        return limit > 0 ? all.stream().limit(limit).collect(Collectors.toList()) : all;
    }

    public FitnessProgressDTO getProgressStats(String username) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        List<FitnessSessionLog> allLogs = sessionLogRepository.findByFitnessProfileOrderByLogDateDesc(profile);
        if (allLogs == null) {
            allLogs = new ArrayList<>();
        }

        // Group logs by ISO week key (year * 100 + week)
        Map<Integer, List<FitnessSessionLog>> byWeek = allLogs.stream()
                .filter(l -> l.getLogDate() != null)
                .collect(Collectors.groupingBy(l -> {
                    int year = l.getLogDate().get(IsoFields.WEEK_BASED_YEAR);
                    int week = l.getLogDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    return year * 100 + week;
                }));

        // Build sorted list of last 8 weeks
        List<Integer> sortedWeekKeys = byWeek.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .limit(8)
                .sorted()
                .collect(Collectors.toList());

        List<String> weekLabels = new ArrayList<>();
        List<Integer> trainingDays = new ArrayList<>();
        List<Double> runningKm = new ArrayList<>();
        List<Double> avgDifficulty = new ArrayList<>();

        for (int key : sortedWeekKeys) {
            int week = key % 100;
            int year = key / 100;
            weekLabels.add("KW " + week + "/" + year);

            List<FitnessSessionLog> weekLogs = byWeek.get(key);
            List<FitnessSessionLog> completed = weekLogs.stream()
                    .filter(l -> l.getStatus() == SessionStatus.COMPLETED)
                    .collect(Collectors.toList());

            trainingDays.add(completed.size());

            double km = completed.stream()
                    .filter(l -> l.getSessionType() == SessionType.RUNNING)
                    .mapToDouble(l -> {
                        List<FitnessSessionExerciseLog> exLogs = sessionExerciseLogRepository
                                .findBySessionLogOrderByOrderIndex(l);
                        return exLogs.stream()
                                .filter(e -> e.getDistanceMeters() != null)
                                .mapToInt(FitnessSessionExerciseLog::getDistanceMeters)
                                .sum() / 1000.0;
                    })
                    .sum();
            runningKm.add(Math.round(km * 100.0) / 100.0);

            OptionalDouble avg = completed.stream()
                    .filter(l -> l.getDifficultyRating() != null)
                    .mapToInt(FitnessSessionLog::getDifficultyRating)
                    .average();
            avgDifficulty.add(avg.isPresent() ? Math.round(avg.getAsDouble() * 10.0) / 10.0 : 0.0);
        }

        long totalCompleted = allLogs.stream().filter(l -> l.getStatus() == SessionStatus.COMPLETED).count();
        long totalSkipped = allLogs.stream().filter(l -> l.getStatus() == SessionStatus.SKIPPED).count();

        double totalKm = allLogs.stream()
                .filter(l -> l.getStatus() == SessionStatus.COMPLETED && l.getSessionType() == SessionType.RUNNING)
                .mapToDouble(l -> {
                    List<FitnessSessionExerciseLog> exLogs = sessionExerciseLogRepository
                            .findBySessionLogOrderByOrderIndex(l);
                    return exLogs.stream()
                            .filter(e -> e.getDistanceMeters() != null)
                            .mapToInt(FitnessSessionExerciseLog::getDistanceMeters)
                            .sum() / 1000.0;
                })
                .sum();

        int[] streaks = calculateStreaks(allLogs);

        return new FitnessProgressDTO(
                weekLabels, trainingDays, runningKm, avgDifficulty,
                (int) totalCompleted, (int) totalSkipped,
                Math.round(totalKm * 100.0) / 100.0,
                streaks[0], streaks[1]
        );
    }

    /** Returns [currentStreak, longestStreak] in weeks with at least 1 completed session. */
    private int[] calculateStreaks(List<FitnessSessionLog> allLogs) {
        List<Long> absoluteWeeks = allLogs.stream()
                .filter(l -> l.getStatus() == SessionStatus.COMPLETED && l.getLogDate() != null)
                .map(l -> weekToAbsolute(l.getLogDate()))
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (absoluteWeeks.isEmpty()) return new int[]{0, 0};

        int longest = 1, run = 1;
        for (int i = 1; i < absoluteWeeks.size(); i++) {
            if (absoluteWeeks.get(i) - absoluteWeeks.get(i - 1) == 1) {
                run++;
                longest = Math.max(longest, run);
            } else {
                run = 1;
            }
        }

        long currentWeek = weekToAbsolute(LocalDate.now());
        int current = 0;
        for (int i = absoluteWeeks.size() - 1; i >= 0; i--) {
            long w = absoluteWeeks.get(i);
            if (w > currentWeek) continue;
            if (current == 0 && currentWeek - w <= 1) {
                current = 1;
            } else if (current > 0 && absoluteWeeks.get(i + 1) - w == 1) {
                current++;
            } else {
                break;
            }
        }

        return new int[]{current, longest};
    }

    private long weekToAbsolute(LocalDate date) {
        return date.with(DayOfWeek.MONDAY).toEpochDay() / 7;
    }

    public List<SessionSummaryDTO> getSessionHistoryWithStats(String username, int limit) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        List<FitnessSessionLog> all = sessionLogRepository.findByFitnessProfileOrderByLogDateDesc(profile);
        List<FitnessSessionLog> logs = limit > 0 ? all.stream().limit(limit).collect(Collectors.toList()) : all;

        return logs.stream().map(log -> {
            Integer totalDist = null;
            String paceDisplay = null;
            if (log.getStatus() == SessionStatus.COMPLETED && log.getSessionType() == SessionType.RUNNING) {
                List<FitnessSessionExerciseLog> exLogs = sessionExerciseLogRepository.findBySessionLogOrderByOrderIndex(log);
                int dist = exLogs.stream().filter(e -> e.getDistanceMeters() != null)
                        .mapToInt(FitnessSessionExerciseLog::getDistanceMeters).sum();
                int runSec = exLogs.stream().filter(e -> e.getDurationRunSeconds() != null)
                        .mapToInt(FitnessSessionExerciseLog::getDurationRunSeconds).sum();
                if (dist > 0) {
                    totalDist = dist;
                    if (runSec > 0) {
                        double paceSecPerKm = (runSec * 1000.0) / dist;
                        int min = (int) (paceSecPerKm / 60);
                        int sec = (int) (paceSecPerKm % 60);
                        paceDisplay = String.format("%d:%02d min/km", min, sec);
                    }
                }
            }
            return new SessionSummaryDTO(
                    log.getId(), log.getLogDate(), log.getSessionType(), log.getStatus(),
                    log.getSkipReason(), log.getSkipNotes(), log.getActualDurationMinutes(),
                    log.getDifficultyRating(), log.getFeedbackText(), log.getExternalCaloriesBurned(),
                    totalDist, paceDisplay);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PersonalRecordDTO> getPersonalRecords(String username) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        List<FitnessSessionLog> completedLogs = sessionLogRepository
                .findByFitnessProfileOrderByLogDateDesc(profile).stream()
                .filter(l -> l.getStatus() == SessionStatus.COMPLETED)
                .collect(Collectors.toList());

        Map<Long, FitnessExercise> allExercises = exerciseRepository.findAll().stream()
                .collect(Collectors.toMap(FitnessExercise::getId, e -> e));

        Map<Long, Integer> bestValue = new LinkedHashMap<>();
        Map<Long, LocalDate> bestDate = new LinkedHashMap<>();
        Map<Long, Integer> sessionCount = new LinkedHashMap<>();

        for (FitnessSessionLog sessionLog : completedLogs) {
            List<FitnessSessionExerciseLog> exLogs = sessionExerciseLogRepository
                    .findBySessionLogOrderByOrderIndex(sessionLog);
            for (FitnessSessionExerciseLog exLog : exLogs) {
                Long exId = exLog.getExercise().getId();
                FitnessExercise ex = allExercises.get(exId);
                if (ex == null) continue;
                sessionCount.merge(exId, 1, Integer::sum);
                Integer val = extractBestValue(exLog, ex);
                if (val != null && (bestValue.get(exId) == null || val > bestValue.get(exId))) {
                    bestValue.put(exId, val);
                    bestDate.put(exId, sessionLog.getLogDate());
                }
            }
        }

        return allExercises.values().stream()
                .filter(ex -> bestValue.containsKey(ex.getId()))
                .map(ex -> {
                    Long id = ex.getId();
                    Integer best = bestValue.get(id);
                    Integer bestReps = null, bestDur = null, bestDist = null;
                    if (ex.getTrackingType() == ExerciseTrackingType.SETS_REPS) bestReps = best;
                    else if (ex.getTrackingType() == ExerciseTrackingType.TIME_BASED) bestDur = best;
                    else if (ex.getTrackingType() == ExerciseTrackingType.DISTANCE_DURATION) bestDist = best;
                    return new PersonalRecordDTO(id, ex.getName(), ex.getCategory(), ex.getTrackingType(),
                            bestReps, bestDur, bestDist, bestDate.get(id), sessionCount.getOrDefault(id, 0));
                })
                .sorted(Comparator.comparing((PersonalRecordDTO pr) -> pr.getCategory().ordinal())
                        .thenComparing(PersonalRecordDTO::getExerciseName))
                .collect(Collectors.toList());
    }

    public RecoveryReadinessDTO getRecoveryReadiness(String username) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        List<FitnessSessionLog> recent = sessionLogRepository
                .findByFitnessProfileOrderByLogDateDesc(profile).stream()
                .limit(20)
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now();

        boolean recentSick = recent.stream()
                .filter(l -> l.getStatus() == SessionStatus.SKIPPED && l.getSkipReason() == SkipReason.SICK)
                .anyMatch(l -> l.getLogDate() != null && !l.getLogDate().isBefore(today.minusDays(7)));

        boolean recentTeamSport = recent.stream()
                .filter(l -> l.getSessionType() == SessionType.TEAM_SPORT && l.getStatus() == SessionStatus.COMPLETED)
                .anyMatch(l -> l.getLogDate() != null && !l.getLogDate().isBefore(today.minusDays(1)));

        Optional<FitnessSessionLog> lastCompleted = recent.stream()
                .filter(l -> l.getStatus() == SessionStatus.COMPLETED && l.getSessionType() != SessionType.TEAM_SPORT)
                .findFirst();

        long daysSinceLast = lastCompleted
                .map(l -> ChronoUnit.DAYS.between(l.getLogDate(), today))
                .orElse(999L);

        Integer lastDifficulty = lastCompleted.map(FitnessSessionLog::getDifficultyRating).orElse(null);

        if (recentSick) {
            return new RecoveryReadinessDTO("REST_RECOMMENDED", "Erholung empfohlen",
                    "Du hattest in den letzten Tagen eine Krankmeldung. H\u00f6r auf deinen K\u00f6rper.", "danger");
        }
        if (recentTeamSport) {
            return new RecoveryReadinessDTO("TAKE_IT_EASY", "Erholung ber\u00fccksichtigen",
                    "Du hast gestern Fu\u00dfball gespielt. W\u00e4rme dich gut auf und starte nicht zu intensiv.", "warning");
        }
        if (daysSinceLast > 7) {
            return new RecoveryReadinessDTO("EASE_BACK_IN", "Sanft einsteigen",
                    "\u00dcber eine Woche Pause \u2014 starte mit reduzierter Intensit\u00e4t.", "info");
        }
        if (lastDifficulty != null && lastDifficulty >= 4 && daysSinceLast <= 1) {
            return new RecoveryReadinessDTO("TAKE_IT_EASY", "Leicht trainieren",
                    "Letzte Einheit war intensiv (Schwierigkeit " + lastDifficulty + "/5). G\u00f6nne dir heute eine leichtere Einheit.", "warning");
        }
        if (lastDifficulty != null && lastDifficulty <= 2) {
            return new RecoveryReadinessDTO("READY", "Bereit \u2013 Intensit\u00e4t steigern",
                    "Letzte Einheit war locker. Du kannst heute einen Zahn zulegen.", "success");
        }
        return new RecoveryReadinessDTO("READY", "Bereit",
                "Nichts spricht gegen eine normale Trainingseinheit heute.", "success");
    }

    private Integer extractBestValue(FitnessSessionExerciseLog exLog, FitnessExercise ex) {
        if (ex.getTrackingType() == null) return null;
        return switch (ex.getTrackingType()) {
            case SETS_REPS -> exLog.getRepsDone();
            case TIME_BASED -> exLog.getDurationSeconds();
            case DISTANCE_DURATION -> exLog.getDistanceMeters();
        };
    }

    @Transactional
    public FitnessPlan regeneratePlan(String username, String userNotes) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);

        if (userNotes != null && !userNotes.isBlank()) {
            Optional<FitnessPlan> activePlan = fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile);
            int version = activePlan.map(FitnessPlan::getVersion).orElse(0);
            FitnessAssessmentAnswer feedback = new FitnessAssessmentAnswer();
            feedback.setFitnessProfile(profile);
            feedback.setQuestionKey("plan_feedback_" + version);
            feedback.setAnswerValue(userNotes);
            assessmentAnswerRepository.save(feedback);
        }

        return fitnessAiService.regeneratePlan(username);
    }

    @Transactional
    public void switchSessionType(String username, Long sessionId) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        FitnessPlan plan = fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)
                .orElseThrow(() -> new RuntimeException("No active plan found"));

        FitnessPlanSession session = fitnessPlanSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (!session.getFitnessPlan().getId().equals(plan.getId())) {
            throw new RuntimeException("Session does not belong to user's active plan");
        }
        if (sessionLogRepository.findTopByPlanSessionOrderByIdDesc(session).isPresent()) {
            throw new RuntimeException("Session already logged, cannot switch type");
        }

        SessionType oldType = session.getSessionType();
        SessionType newType = oldType == SessionType.RUNNING ? SessionType.BODYWEIGHT : SessionType.RUNNING;
        session.setSessionType(newType);
        fitnessPlanSessionRepository.save(session);

        // Replace exercises: copy from the first other session of the new type in the plan
        fitnessPlanExerciseRepository.deleteByPlanSession(session);
        List<FitnessPlanSession> allSessions = fitnessPlanSessionRepository
                .findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(plan);
        allSessions.stream()
                .filter(s -> !s.getId().equals(sessionId) && s.getSessionType() == newType)
                .findFirst()
                .ifPresent(ref -> {
                    List<FitnessPlanExercise> refExercises = fitnessPlanExerciseRepository
                            .findByPlanSessionOrderByOrderIndex(ref);
                    for (FitnessPlanExercise src : refExercises) {
                        FitnessPlanExercise pe = new FitnessPlanExercise();
                        pe.setPlanSession(session);
                        pe.setExercise(src.getExercise());
                        pe.setOrderIndex(src.getOrderIndex());
                        pe.setTargetSets(src.getTargetSets());
                        pe.setTargetReps(src.getTargetReps());
                        pe.setTargetDurationSeconds(src.getTargetDurationSeconds());
                        pe.setTargetDistanceMeters(src.getTargetDistanceMeters());
                        pe.setTargetDurationRunSeconds(src.getTargetDurationRunSeconds());
                        pe.setRestSeconds(src.getRestSeconds());
                        pe.setNotes(src.getNotes());
                        fitnessPlanExerciseRepository.save(pe);
                    }
                });

        // Save preference note so the AI regeneration is aware of the switch
        FitnessAssessmentAnswer note = new FitnessAssessmentAnswer();
        note.setFitnessProfile(profile);
        note.setQuestionKey("session_type_switch");
        note.setAnswerValue("User switched a session from " + oldType + " to " + newType
                + " (week " + session.getWeekNumber() + ", session " + session.getSessionNumber() + ")");
        assessmentAnswerRepository.save(note);

        log.info("Switched session {} from {} to {} for user {}, triggering AI regeneration",
                sessionId, oldType, newType, username);
        fitnessAiService.regeneratePlan(username);
    }

    @Transactional
    public void resetFitnessData(String username) {
        FitnessProfile profile = getOrCreateFitnessProfile(username);
        log.info("Resetting all fitness data for user {}", username);

        // Delete in dependency order to avoid FK violations
        sessionExerciseLogRepository.deleteByFitnessProfile(profile);
        sessionLogRepository.deleteByFitnessProfile(profile);

        List<FitnessPlan> plans = fitnessPlanRepository.findByFitnessProfileOrderByVersionDesc(profile);
        for (FitnessPlan plan : plans) {
            fitnessPlanExerciseRepository.deleteByPlan(plan);
            fitnessPlanSessionRepository.deleteByFitnessPlan(plan);
        }
        fitnessPlanRepository.deleteByFitnessProfile(profile);

        assessmentAnswerRepository.deleteByFitnessProfile(profile);
        weightLogRepository.deleteByFitnessProfile(profile);
        mealLogRepository.deleteByFitnessProfile(profile);

        profile.setAssessmentCompleted(false);
        profile.setCurrentPlanId(null);
        profile.setHeightCm(null);
        profile.setBirthYear(null);
        profile.setBiologicalSex(null);
        profile.setActivityLevel(null);
        profile.setTargetWeightKg(null);
        profile.setTargetProteinG(null);
        profile.setTargetCarbsG(null);
        fitnessProfileRepository.save(profile);
    }

    private void checkAndTriggerEvolution(String username, FitnessProfile profile, FitnessPlanSession justLoggedSession) {
        if (justLoggedSession == null) return;

        FitnessPlan plan = justLoggedSession.getFitnessPlan();
        int weekNumber = justLoggedSession.getWeekNumber();

        List<FitnessPlanSession> sessionsInWeek = fitnessPlanSessionRepository
                .findByFitnessPlanAndWeekNumber(plan, weekNumber);

        boolean allDone = sessionsInWeek.stream()
                .allMatch(s -> sessionLogRepository.findTopByPlanSessionOrderByIdDesc(s).isPresent());

        if (allDone) {
            log.info("Week {} of plan {} is complete, triggering AI evolution for user {}",
                    weekNumber, plan.getId(), username);
            try {
                fitnessAiService.evolvePlan(username);
            } catch (FitnessAiException e) {
                log.warn("Failed to evolve plan for user {}: {}", username, e.getMessage());
            }
        }
    }

    private FitnessPlanDTO toPlanDTO(FitnessPlan plan) {
        if (plan == null) {
            return null;
        }
        List<FitnessPlanSession> sessions = fitnessPlanSessionRepository
                .findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(plan);

        List<FitnessPlanSessionDTO> sessionDTOs = sessions != null ? sessions.stream()
                .filter(Objects::nonNull)
                .map(this::toSessionDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()) : new ArrayList<>();

        return new FitnessPlanDTO(
                plan.getId(), plan.getVersion(), plan.getGeneratedAt(),
                plan.isActive(), plan.getGenerationReason(), plan.getAiNotes(), sessionDTOs
        );
    }

    private FitnessPlanSessionDTO toSessionDTO(FitnessPlanSession session) {
        List<FitnessPlanExercise> exercises = fitnessPlanExerciseRepository
                .findByPlanSessionOrderByOrderIndex(session);

        Optional<FitnessSessionLog> logOpt = sessionLogRepository.findTopByPlanSessionOrderByIdDesc(session);

        List<FitnessPlanExerciseDTO> exerciseDTOs = exercises != null ? exercises.stream()
                .map(this::toExerciseDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()) : new ArrayList<>();

        return new FitnessPlanSessionDTO(
                session.getId(), session.getSessionNumber(), session.getWeekNumber(),
                session.getSessionType(), session.getEstimatedDurationMinutes(), session.getDescription(),
                exerciseDTOs,
                logOpt.map(FitnessSessionLog::getStatus).orElse(null),
                logOpt.map(FitnessSessionLog::getSkipReason).orElse(null)
        );
    }

    private FitnessPlanExerciseDTO toExerciseDTO(FitnessPlanExercise pe) {
        if (pe == null || pe.getExercise() == null) {
            return null;
        }
        FitnessExercise ex = pe.getExercise();
        FitnessExerciseDTO exDto = new FitnessExerciseDTO(
                ex.getId(), ex.getName(), ex.getDescription(), ex.getCategory(),
                ex.getTrackingType(), ex.getEquipment(), ex.getDifficulty(),
                ex.getImageUrl(), ex.getVideoUrl()
        );
        return new FitnessPlanExerciseDTO(
                pe.getId(), exDto, pe.getOrderIndex(), pe.getTargetSets(), pe.getTargetReps(),
                pe.getTargetDurationSeconds(), pe.getTargetDistanceMeters(),
                pe.getTargetDurationRunSeconds(), pe.getRestSeconds(), pe.getNotes()
        );
    }
}

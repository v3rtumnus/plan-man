package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dao.fitness.*;
import at.v3rtumnus.planman.dto.fitness.*;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.fitness.*;
import at.v3rtumnus.planman.exception.FitnessAiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FitnessServiceTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private FitnessProfileRepository fitnessProfileRepository;
    @Mock private FitnessAssessmentAnswerRepository assessmentAnswerRepository;
    @Mock private FitnessPlanRepository fitnessPlanRepository;
    @Mock private FitnessPlanSessionRepository fitnessPlanSessionRepository;
    @Mock private FitnessPlanExerciseRepository fitnessPlanExerciseRepository;
    @Mock private FitnessExerciseRepository exerciseRepository;
    @Mock private FitnessSessionLogRepository sessionLogRepository;
    @Mock private FitnessSessionExerciseLogRepository sessionExerciseLogRepository;
    @Mock private FitnessAiService fitnessAiService;

    @InjectMocks
    private FitnessService service;

    private FitnessProfile profileWithUser(String username) {
        UserProfile up = new UserProfile();
        up.setUsername(username);
        FitnessProfile profile = new FitnessProfile();
        profile.setId(1L);
        profile.setUserProfile(up);
        profile.setCreatedAt(LocalDateTime.now());
        return profile;
    }

    // ===== getOrCreateFitnessProfile =====

    @Test
    void getOrCreateFitnessProfile_existingProfile_returnsIt() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));

        FitnessProfile result = service.getOrCreateFitnessProfile("alice");

        assertThat(result).isSameAs(profile);
        verify(fitnessProfileRepository, never()).save(any());
    }

    @Test
    void getOrCreateFitnessProfile_userNotFound_throwsRuntimeException() {
        when(fitnessProfileRepository.findByUserProfileUsername("ghost")).thenReturn(Optional.empty());
        when(userProfileRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreateFitnessProfile("ghost"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void getOrCreateFitnessProfile_noProfile_createsNew() {
        UserProfile up = new UserProfile();
        up.setUsername("bob");
        FitnessProfile saved = profileWithUser("bob");

        when(fitnessProfileRepository.findByUserProfileUsername("bob")).thenReturn(Optional.empty());
        when(userProfileRepository.findByUsername("bob")).thenReturn(Optional.of(up));
        when(fitnessProfileRepository.save(any())).thenReturn(saved);

        FitnessProfile result = service.getOrCreateFitnessProfile("bob");

        assertThat(result).isSameAs(saved);
        verify(fitnessProfileRepository).save(any(FitnessProfile.class));
    }

    // ===== saveAssessmentAnswers =====

    @Test
    void saveAssessmentAnswers_savesAllAnswersAndSetsFlag() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessProfileRepository.save(any())).thenReturn(profile);

        List<AssessmentAnswerDTO> answers = List.of(
                new AssessmentAnswerDTO("training_days", "3"),
                new AssessmentAnswerDTO("focus_area", "CORE")
        );

        service.saveAssessmentAnswers("alice", answers);

        verify(assessmentAnswerRepository).deleteByFitnessProfile(profile);
        verify(assessmentAnswerRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
        assertThat(profile.isAssessmentCompleted()).isTrue();
    }

    // ===== getActivePlan =====

    @Test
    void getActivePlan_noPlan_returnsNull() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.empty());

        FitnessPlanDTO result = service.getActivePlan("alice");

        assertThat(result).isNull();
    }

    @Test
    void getActivePlan_withPlan_returnsMappedDTO() {
        FitnessProfile profile = profileWithUser("alice");
        FitnessPlan plan = buildPlan(profile, 1);

        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.of(plan));
        when(fitnessPlanSessionRepository.findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(plan))
                .thenReturn(List.of());

        FitnessPlanDTO dto = service.getActivePlan("alice");

        assertThat(dto).isNotNull();
        assertThat(dto.getVersion()).isEqualTo(1);
        assertThat(dto.getAiNotes()).isEqualTo("Test notes");
    }

    @Test
    void getActivePlan_withSessionsAndExercises_convertsAllDTOs() {
        FitnessProfile profile = profileWithUser("alice");
        FitnessPlan plan = buildPlan(profile, 2);

        FitnessPlanSession session = new FitnessPlanSession();
        session.setId(10L);
        session.setFitnessPlan(plan);
        session.setWeekNumber(1);
        session.setSessionNumber(1);
        session.setSessionType(SessionType.BODYWEIGHT);
        session.setEstimatedDurationMinutes(45);
        session.setDescription("Leg day");

        FitnessExercise exercise = new FitnessExercise();
        exercise.setId(5L);
        exercise.setName("Squat");
        exercise.setCategory(ExerciseCategory.LOWER_BODY);
        exercise.setTrackingType(ExerciseTrackingType.SETS_REPS);
        exercise.setEquipment(Equipment.NONE);

        FitnessPlanExercise planEx = new FitnessPlanExercise();
        planEx.setId(20L);
        planEx.setExercise(exercise);
        planEx.setOrderIndex(1);
        planEx.setTargetSets(3);
        planEx.setTargetReps(12);

        FitnessSessionLog sessionLog = new FitnessSessionLog();
        sessionLog.setStatus(SessionStatus.COMPLETED);

        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.of(plan));
        when(fitnessPlanSessionRepository.findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(plan))
                .thenReturn(List.of(session));
        when(fitnessPlanExerciseRepository.findByPlanSessionOrderByOrderIndex(session))
                .thenReturn(List.of(planEx));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(session)).thenReturn(Optional.of(sessionLog));

        FitnessPlanDTO dto = service.getActivePlan("alice");

        assertThat(dto.getSessions()).hasSize(1);
        FitnessPlanSessionDTO sessionDTO = dto.getSessions().get(0);
        assertThat(sessionDTO.getExercises()).hasSize(1);
        assertThat(sessionDTO.getExercises().get(0).getExercise().getName()).isEqualTo("Squat");
        assertThat(sessionDTO.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    // ===== saveSessionLog =====

    @Test
    void saveSessionLog_withExercises_savesLogAndExercises() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));

        FitnessSessionLog savedLog = new FitnessSessionLog();
        savedLog.setId(5L);
        when(sessionLogRepository.save(any())).thenReturn(savedLog);

        ExerciseLogDTO exDto = new ExerciseLogDTO();
        exDto.setExerciseId(1L);
        exDto.setOrderIndex(1);
        exDto.setSetNumber(1);
        exDto.setRepsDone(10);

        SessionLogDTO dto = new SessionLogDTO();
        dto.setSessionType(SessionType.BODYWEIGHT);
        dto.setLogDate(LocalDate.now());
        dto.setActualDurationMinutes(30);
        dto.setDifficultyRating(3);
        dto.setExercises(List.of(exDto));

        FitnessSessionLog result = service.saveSessionLog("alice", dto);

        assertThat(result.getId()).isEqualTo(5L);
        verify(sessionExerciseLogRepository).saveAll(any());
    }

    @Test
    void saveSessionLog_withPlanSession_triggersEvolutionCheck() {
        FitnessProfile profile = profileWithUser("alice");
        FitnessPlan plan = buildPlan(profile, 1);
        FitnessPlanSession planSession = new FitnessPlanSession();
        planSession.setId(10L);
        planSession.setFitnessPlan(plan);
        planSession.setWeekNumber(1);
        planSession.setSessionType(SessionType.BODYWEIGHT);

        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanSessionRepository.findById(10L)).thenReturn(Optional.of(planSession));
        when(sessionLogRepository.save(any())).thenReturn(new FitnessSessionLog());
        when(fitnessPlanSessionRepository.findByFitnessPlanAndWeekNumber(plan, 1))
                .thenReturn(List.of(planSession));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(planSession)).thenReturn(Optional.of(new FitnessSessionLog()));

        SessionLogDTO dto = new SessionLogDTO();
        dto.setPlanSessionId(10L);
        dto.setSessionType(SessionType.BODYWEIGHT);

        service.saveSessionLog("alice", dto);

        // Week complete (single session logged) → evolution triggered
        verify(fitnessAiService).evolvePlan("alice");
    }

    @Test
    void saveSessionLog_weekNotComplete_noEvolution() {
        FitnessProfile profile = profileWithUser("alice");
        FitnessPlan plan = buildPlan(profile, 1);
        FitnessPlanSession session1 = buildPlanSession(plan, 1, 1);
        FitnessPlanSession session2 = buildPlanSession(plan, 1, 2);

        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanSessionRepository.findById(1L)).thenReturn(Optional.of(session1));
        when(sessionLogRepository.save(any())).thenReturn(new FitnessSessionLog());
        when(fitnessPlanSessionRepository.findByFitnessPlanAndWeekNumber(plan, 1))
                .thenReturn(List.of(session1, session2));
        // session1 logged, session2 not
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(session1)).thenReturn(Optional.of(new FitnessSessionLog()));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(session2)).thenReturn(Optional.empty());

        SessionLogDTO dto = new SessionLogDTO();
        dto.setPlanSessionId(1L);
        dto.setSessionType(SessionType.BODYWEIGHT);

        service.saveSessionLog("alice", dto);

        verify(fitnessAiService, never()).evolvePlan(any());
    }

    // ===== skipSession =====

    @Test
    void skipSession_createsSkipLog() {
        FitnessProfile profile = profileWithUser("alice");
        FitnessPlan plan = buildPlan(profile, 1);
        FitnessPlanSession planSession = buildPlanSession(plan, 1, 1);

        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanSessionRepository.findById(1L)).thenReturn(Optional.of(planSession));
        when(sessionLogRepository.save(any())).thenReturn(new FitnessSessionLog());
        when(fitnessPlanSessionRepository.findByFitnessPlanAndWeekNumber(plan, 1))
                .thenReturn(List.of(planSession));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(planSession)).thenReturn(Optional.of(new FitnessSessionLog()));

        service.skipSession("alice", 1L, SkipReason.SICK, "feeling unwell");

        ArgumentCaptor<FitnessSessionLog> captor = ArgumentCaptor.forClass(FitnessSessionLog.class);
        verify(sessionLogRepository).save(captor.capture());
        FitnessSessionLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.SKIPPED);
        assertThat(saved.getSkipReason()).isEqualTo(SkipReason.SICK);
        assertThat(saved.getSkipNotes()).isEqualTo("feeling unwell");
    }

    // ===== getSessionHistory =====

    @Test
    void getSessionHistory_withLimit_returnsLimitedList() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));

        List<FitnessSessionLog> logs = List.of(
                new FitnessSessionLog(), new FitnessSessionLog(), new FitnessSessionLog()
        );
        when(sessionLogRepository.findByFitnessProfileOrderByLogDateDesc(profile)).thenReturn(logs);

        List<FitnessSessionLog> result = service.getSessionHistory("alice", 2);
        assertThat(result).hasSize(2);
    }

    @Test
    void getSessionHistory_noLimit_returnsAll() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));

        List<FitnessSessionLog> logs = List.of(new FitnessSessionLog(), new FitnessSessionLog());
        when(sessionLogRepository.findByFitnessProfileOrderByLogDateDesc(profile)).thenReturn(logs);

        List<FitnessSessionLog> result = service.getSessionHistory("alice", 0);
        assertThat(result).hasSize(2);
    }

    // ===== getProgressStats =====

    @Test
    void getProgressStats_noLogs_returnsEmptyStats() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(sessionLogRepository.findByFitnessProfileOrderByLogDateDesc(profile)).thenReturn(List.of());

        FitnessProgressDTO result = service.getProgressStats("alice");

        assertThat(result).isNotNull();
        assertThat(result.getTotalSessionsCompleted()).isEqualTo(0);
        assertThat(result.getTotalSessionsSkipped()).isEqualTo(0);
        assertThat(result.getTotalRunningKm()).isEqualTo(0.0);
        assertThat(result.getWeekLabels()).isEmpty();
    }

    @Test
    void getProgressStats_withCompletedLogs_calculatesStats() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));

        FitnessSessionLog completed = new FitnessSessionLog();
        completed.setStatus(SessionStatus.COMPLETED);
        completed.setSessionType(SessionType.BODYWEIGHT);
        completed.setLogDate(LocalDate.now());
        completed.setDifficultyRating(4);

        FitnessSessionLog skipped = new FitnessSessionLog();
        skipped.setStatus(SessionStatus.SKIPPED);
        skipped.setSessionType(SessionType.RUNNING);
        skipped.setLogDate(LocalDate.now());

        when(sessionLogRepository.findByFitnessProfileOrderByLogDateDesc(profile))
                .thenReturn(List.of(completed, skipped));
        when(sessionExerciseLogRepository.findBySessionLogOrderByOrderIndex(any()))
                .thenReturn(List.of());

        FitnessProgressDTO result = service.getProgressStats("alice");

        assertThat(result.getTotalSessionsCompleted()).isEqualTo(1);
        assertThat(result.getTotalSessionsSkipped()).isEqualTo(1);
    }

    @Test
    void getProgressStats_withRunningLog_calculatesKm() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));

        FitnessSessionLog runLog = new FitnessSessionLog();
        runLog.setStatus(SessionStatus.COMPLETED);
        runLog.setSessionType(SessionType.RUNNING);
        runLog.setLogDate(LocalDate.now());
        runLog.setDifficultyRating(3);

        FitnessSessionExerciseLog exLog = new FitnessSessionExerciseLog();
        exLog.setDistanceMeters(5000); // 5km

        when(sessionLogRepository.findByFitnessProfileOrderByLogDateDesc(profile))
                .thenReturn(List.of(runLog));
        when(sessionExerciseLogRepository.findBySessionLogOrderByOrderIndex(runLog))
                .thenReturn(List.of(exLog));

        FitnessProgressDTO result = service.getProgressStats("alice");

        assertThat(result.getTotalRunningKm()).isEqualTo(5.0);
        assertThat(result.getRunningKmPerWeek().get(0)).isEqualTo(5.0);
    }

    // ===== regeneratePlan =====

    @Test
    void regeneratePlan_withNotes_savesFeedbackAndCallsAi() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.empty());
        when(fitnessAiService.regeneratePlan("alice")).thenReturn(new FitnessPlan());

        service.regeneratePlan("alice", "More running please");

        verify(assessmentAnswerRepository).save(argThat(a ->
                ((FitnessAssessmentAnswer) a).getAnswerValue().equals("More running please")));
        verify(fitnessAiService).regeneratePlan("alice");
    }

    @Test
    void regeneratePlan_noNotes_callsAiWithoutSavingFeedback() {
        FitnessProfile profile = profileWithUser("alice");
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessAiService.regeneratePlan("alice")).thenReturn(new FitnessPlan());

        service.regeneratePlan("alice", null);

        verify(assessmentAnswerRepository, never()).save(any());
        verify(fitnessAiService).regeneratePlan("alice");
    }

    // ===== resetFitnessData =====

    @Test
    void resetFitnessData_deletesAllAndResetsProfile() {
        FitnessProfile profile = profileWithUser("alice");
        profile.setAssessmentCompleted(true);
        profile.setCurrentPlanId(42L);
        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessProfileRepository.save(any())).thenReturn(profile);

        service.resetFitnessData("alice");

        verify(sessionLogRepository).deleteByFitnessProfile(profile);
        verify(fitnessPlanRepository).deleteByFitnessProfile(profile);
        verify(assessmentAnswerRepository).deleteByFitnessProfile(profile);
        assertThat(profile.isAssessmentCompleted()).isFalse();
        assertThat(profile.getCurrentPlanId()).isNull();
    }

    // ===== switchSessionType =====

    @Test
    void switchSessionType_runningToBodyweight_copiesExercisesAndRegenerates() {
        FitnessProfile profile = profileWithUser("alice");
        FitnessPlan plan = buildPlan(profile, 1);
        FitnessPlanSession runSession = buildPlanSession(plan, 1, 1);
        runSession.setSessionType(SessionType.RUNNING);
        FitnessPlanSession bwSession = buildPlanSession(plan, 1, 2);
        bwSession.setSessionType(SessionType.BODYWEIGHT);

        FitnessExercise ex = new FitnessExercise();
        ex.setId(5L);
        FitnessPlanExercise planEx = new FitnessPlanExercise();
        planEx.setExercise(ex);
        planEx.setOrderIndex(1);
        planEx.setTargetSets(3);
        planEx.setTargetReps(10);
        planEx.setRestSeconds(60);

        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.of(plan));
        when(fitnessPlanSessionRepository.findById(1L)).thenReturn(Optional.of(runSession));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(runSession)).thenReturn(Optional.empty());
        when(fitnessPlanSessionRepository.findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(plan))
                .thenReturn(List.of(runSession, bwSession));
        when(fitnessPlanExerciseRepository.findByPlanSessionOrderByOrderIndex(bwSession))
                .thenReturn(List.of(planEx));
        when(fitnessAiService.regeneratePlan("alice")).thenReturn(new FitnessPlan());

        service.switchSessionType("alice", 1L);

        verify(fitnessPlanExerciseRepository).deleteByPlanSession(runSession);
        verify(fitnessPlanExerciseRepository).save(any(FitnessPlanExercise.class));
        verify(assessmentAnswerRepository).save(any(FitnessAssessmentAnswer.class));
        verify(fitnessAiService).regeneratePlan("alice");
    }

    @Test
    void switchSessionType_alreadyLogged_throwsException() {
        FitnessProfile profile = profileWithUser("alice");
        FitnessPlan plan = buildPlan(profile, 1);
        FitnessPlanSession session = buildPlanSession(plan, 1, 1);

        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.of(plan));
        when(fitnessPlanSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(session)).thenReturn(Optional.of(new FitnessSessionLog()));

        assertThatThrownBy(() -> service.switchSessionType("alice", 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already logged");
    }

    // ===== evolution - AI exception handled gracefully =====

    @Test
    void saveSessionLog_weekCompleteButEvolutionFails_doesNotPropagate() {
        FitnessProfile profile = profileWithUser("alice");
        FitnessPlan plan = buildPlan(profile, 1);
        FitnessPlanSession planSession = buildPlanSession(plan, 1, 1);

        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(fitnessPlanSessionRepository.findById(1L)).thenReturn(Optional.of(planSession));
        when(sessionLogRepository.save(any())).thenReturn(new FitnessSessionLog());
        when(fitnessPlanSessionRepository.findByFitnessPlanAndWeekNumber(plan, 1))
                .thenReturn(List.of(planSession));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(planSession)).thenReturn(Optional.of(new FitnessSessionLog()));
        doThrow(new FitnessAiException("AI failed")).when(fitnessAiService).evolvePlan("alice");

        // Should not throw
        service.saveSessionLog("alice", buildSessionDto(null, SessionType.BODYWEIGHT, 1L));
    }

    // ===== Helpers =====

    private FitnessPlan buildPlan(FitnessProfile profile, int version) {
        FitnessPlan plan = new FitnessPlan();
        plan.setId((long) version);
        plan.setFitnessProfile(profile);
        plan.setVersion(version);
        plan.setGeneratedAt(LocalDateTime.now());
        plan.setActive(true);
        plan.setAiNotes("Test notes");
        return plan;
    }

    private FitnessPlanSession buildPlanSession(FitnessPlan plan, int week, int sessionNum) {
        FitnessPlanSession s = new FitnessPlanSession();
        s.setId((long) sessionNum);
        s.setFitnessPlan(plan);
        s.setWeekNumber(week);
        s.setSessionNumber(sessionNum);
        s.setSessionType(SessionType.BODYWEIGHT);
        return s;
    }

    private SessionLogDTO buildSessionDto(Long planSessionId, SessionType type, Long findSessionById) {
        SessionLogDTO dto = new SessionLogDTO();
        dto.setPlanSessionId(findSessionById);
        dto.setSessionType(type);
        return dto;
    }
}

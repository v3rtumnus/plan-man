package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dao.fitness.*;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.fitness.*;
import at.v3rtumnus.planman.exception.FitnessAiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Spy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FitnessAiServiceTest {

    @Mock private OpenAiChatModel chatModel;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private FitnessProfileRepository fitnessProfileRepository;
    @Mock private FitnessAssessmentAnswerRepository assessmentAnswerRepository;
    @Mock private FitnessExerciseRepository exerciseRepository;
    @Mock private FitnessPlanRepository fitnessPlanRepository;
    @Mock private FitnessPlanSessionRepository fitnessPlanSessionRepository;
    @Mock private FitnessPlanExerciseRepository fitnessPlanExerciseRepository;
    @Mock private FitnessSessionLogRepository sessionLogRepository;
    @Mock private FitnessSessionExerciseLogRepository sessionExerciseLogRepository;

    @InjectMocks
    private FitnessAiService service;

    private FitnessProfile profile;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "aiTimeoutSeconds", 5);

        UserProfile up = new UserProfile();
        up.setUsername("alice");
        profile = new FitnessProfile();
        profile.setId(1L);
        profile.setUserProfile(up);
        profile.setCreatedAt(LocalDateTime.now());

        when(fitnessProfileRepository.findByUserProfileUsername("alice")).thenReturn(Optional.of(profile));
        when(assessmentAnswerRepository.findByFitnessProfile(profile)).thenReturn(List.of());
        when(exerciseRepository.findAll()).thenReturn(List.of());
        when(fitnessPlanRepository.findByFitnessProfileOrderByVersionDesc(profile)).thenReturn(List.of());

        FitnessPlan savedPlan = new FitnessPlan();
        savedPlan.setId(10L);
        savedPlan.setVersion(1);
        when(fitnessPlanRepository.save(any())).thenReturn(savedPlan);
        when(fitnessProfileRepository.save(any())).thenReturn(profile);
    }

    // ===== generateInitialPlan =====

    @Test
    void generateInitialPlan_success_persistsAndReturns() {
        String json = "{\"ai_notes\":\"Start slow\",\"exercise_refresh\":false,\"sessions\":[]}";
        mockChatResponse(json);

        FitnessPlan result = service.generateInitialPlan("alice");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        verify(fitnessPlanRepository).save(any(FitnessPlan.class));
    }

    @Test
    void generateInitialPlan_withSession_persistsSession() {
        String json = """
                {
                  "ai_notes": "Good",
                  "exercise_refresh": false,
                  "sessions": [{
                    "week": 1,
                    "session_number": 1,
                    "session_type": "BODYWEIGHT",
                    "description": "Easy intro",
                    "estimated_duration_minutes": 30,
                    "exercises": []
                  }]
                }
                """;
        mockChatResponse(json);
        when(fitnessPlanSessionRepository.save(any())).thenReturn(new FitnessPlanSession());

        FitnessPlan result = service.generateInitialPlan("alice");

        assertThat(result).isNotNull();
        verify(fitnessPlanSessionRepository).save(any(FitnessPlanSession.class));
    }

    @Test
    void generateInitialPlan_withMarkdownFences_parsesCorrectly() {
        String json = "```json\n{\"ai_notes\":\"OK\",\"exercise_refresh\":false,\"sessions\":[]}\n```";
        mockChatResponse(json);

        FitnessPlan result = service.generateInitialPlan("alice");

        assertThat(result).isNotNull();
    }

    @Test
    void generateInitialPlan_noProfile_createsNewProfile() {
        UserProfile up = new UserProfile();
        up.setUsername("carol");
        FitnessProfile newProfile = new FitnessProfile();
        newProfile.setId(99L);
        newProfile.setUserProfile(up); // required so persistPlan can log username

        when(fitnessProfileRepository.findByUserProfileUsername("carol")).thenReturn(Optional.empty());
        when(userProfileRepository.findByUsername("carol")).thenReturn(Optional.of(up));
        when(fitnessProfileRepository.save(any())).thenReturn(newProfile);

        String json = "{\"ai_notes\":\"OK\",\"exercise_refresh\":false,\"sessions\":[]}";
        mockChatResponse(json);

        service.generateInitialPlan("carol");

        verify(fitnessProfileRepository, atLeastOnce()).save(any(FitnessProfile.class));
    }

    @Test
    void generateInitialPlan_withExistingPlan_deactivatesOld() {
        FitnessPlan existing = new FitnessPlan();
        existing.setId(5L);
        existing.setVersion(1);
        existing.setActive(true);

        when(fitnessPlanRepository.findByFitnessProfileOrderByVersionDesc(profile))
                .thenReturn(List.of(existing));

        mockChatResponse("{\"ai_notes\":\"New\",\"exercise_refresh\":false,\"sessions\":[]}");

        service.generateInitialPlan("alice");

        ArgumentCaptor<FitnessPlan> captor = ArgumentCaptor.forClass(FitnessPlan.class);
        verify(fitnessPlanRepository, atLeast(2)).save(captor.capture());
        boolean deactivated = captor.getAllValues().stream()
                .anyMatch(p -> !p.isActive() && Long.valueOf(5L).equals(p.getId()));
        assertThat(deactivated).isTrue();
    }

    @Test
    void generateInitialPlan_withExerciseInSession_persistsExercise() {
        String json = """
                {
                  "ai_notes": "OK",
                  "exercise_refresh": false,
                  "sessions": [{
                    "week": 1,
                    "session_number": 1,
                    "session_type": "BODYWEIGHT",
                    "exercises": [{
                      "exercise_id": 1,
                      "order_index": 1,
                      "target_sets": 3,
                      "target_reps": 10
                    }]
                  }]
                }
                """;
        mockChatResponse(json);

        FitnessExercise ex = new FitnessExercise();
        ex.setId(1L);
        ex.setName("Push Up");
        when(exerciseRepository.findAll()).thenReturn(List.of(ex));

        FitnessPlanSession savedSession = new FitnessPlanSession();
        savedSession.setId(50L);
        when(fitnessPlanSessionRepository.save(any())).thenReturn(savedSession);

        service.generateInitialPlan("alice");

        verify(fitnessPlanExerciseRepository).save(any(FitnessPlanExercise.class));
    }

    @Test
    void evolvePlan_withNewExercisesFromAi_persistsNewExercises() {
        FitnessPlan activePlan = new FitnessPlan();
        activePlan.setId(1L);
        activePlan.setFitnessProfile(profile);
        activePlan.setActive(true);
        activePlan.setVersion(1);

        FitnessPlanSession session = new FitnessPlanSession();
        session.setId(100L);
        session.setWeekNumber(1);
        session.setSessionNumber(1);
        session.setSessionType(SessionType.BODYWEIGHT);

        FitnessSessionLog log = new FitnessSessionLog();
        log.setId(200L);
        log.setAiAnalyzed(false);
        log.setStatus(SessionStatus.COMPLETED);
        log.setSessionType(SessionType.BODYWEIGHT);
        log.setPlanSession(session);

        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.of(activePlan));
        when(fitnessPlanSessionRepository.findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(activePlan))
                .thenReturn(List.of(session));
        when(fitnessPlanSessionRepository.findByFitnessPlanAndWeekNumber(activePlan, 1))
                .thenReturn(List.of(session));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(session)).thenReturn(Optional.of(log));
        when(fitnessPlanRepository.findByFitnessProfileOrderByVersionDesc(profile))
                .thenReturn(List.of(activePlan));
        when(fitnessPlanRepository.save(any())).thenReturn(new FitnessPlan());

        String json = """
                {
                  "ai_notes": "Refresh",
                  "exercise_refresh": true,
                  "sessions": [],
                  "new_exercises": [{
                    "name": "New Custom Exercise",
                    "description": "Custom variant",
                    "category": "CORE",
                    "tracking_type": "TIME_BASED",
                    "equipment": "NONE",
                    "difficulty": 4
                  }]
                }
                """;
        mockChatResponse(json);

        service.evolvePlan("alice");

        verify(exerciseRepository).save(argThat(e ->
                ((FitnessExercise) e).getName().equals("New Custom Exercise")));
    }

    @Test
    void generateInitialPlan_emptyResponse_throwsFitnessAiException() {
        mockChatResponse("   "); // blank response

        assertThatThrownBy(() -> service.generateInitialPlan("alice"))
                .isInstanceOf(FitnessAiException.class);
    }

    @Test
    void generateInitialPlan_invalidJson_throwsFitnessAiException() {
        mockChatResponse("not-json-at-all");

        assertThatThrownBy(() -> service.generateInitialPlan("alice"))
                .isInstanceOf(FitnessAiException.class);
    }

    @Test
    void generateInitialPlan_modelThrows_throwsFitnessAiException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("API error"));

        assertThatThrownBy(() -> service.generateInitialPlan("alice"))
                .isInstanceOf(FitnessAiException.class);
    }

    // ===== regeneratePlan =====

    @Test
    void regeneratePlan_success_createsManualPlan() {
        String json = "{\"ai_notes\":\"Manual regen\",\"exercise_refresh\":false,\"sessions\":[]}";
        mockChatResponse(json);

        FitnessPlan result = service.regeneratePlan("alice");

        assertThat(result).isNotNull();
        ArgumentCaptor<FitnessPlan> captor = ArgumentCaptor.forClass(FitnessPlan.class);
        verify(fitnessPlanRepository).save(captor.capture());
        assertThat(captor.getValue().getGenerationReason()).isEqualTo(PlanGenerationReason.MANUAL);
    }

    // ===== evolvePlan =====

    @Test
    void evolvePlan_noActivePlan_throwsFitnessAiException() {
        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evolvePlan("alice"))
                .isInstanceOf(FitnessAiException.class);
    }

    @Test
    void evolvePlan_noUnanalyzedWeek_returnsExistingPlan() {
        FitnessPlan activePlan = new FitnessPlan();
        activePlan.setId(1L);
        activePlan.setFitnessProfile(profile);
        activePlan.setActive(true);

        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.of(activePlan));
        when(fitnessPlanSessionRepository.findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(activePlan))
                .thenReturn(List.of()); // no sessions

        FitnessPlan result = service.evolvePlan("alice");

        assertThat(result).isSameAs(activePlan);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void evolvePlan_withCompletedWeek_createsNewPlan() {
        FitnessPlan activePlan = new FitnessPlan();
        activePlan.setId(1L);
        activePlan.setFitnessProfile(profile);
        activePlan.setActive(true);
        activePlan.setVersion(1);

        FitnessPlanSession session = new FitnessPlanSession();
        session.setId(100L);
        session.setWeekNumber(1);
        session.setSessionNumber(1);
        session.setSessionType(SessionType.BODYWEIGHT);

        FitnessSessionLog log = new FitnessSessionLog();
        log.setId(200L);
        log.setAiAnalyzed(false);
        log.setStatus(SessionStatus.COMPLETED);
        log.setSessionType(SessionType.BODYWEIGHT);
        log.setPlanSession(session);

        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.of(activePlan));
        when(fitnessPlanSessionRepository.findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(activePlan))
                .thenReturn(List.of(session));
        when(fitnessPlanSessionRepository.findByFitnessPlanAndWeekNumber(activePlan, 1))
                .thenReturn(List.of(session));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(session)).thenReturn(Optional.of(log));
        when(fitnessPlanRepository.findByFitnessProfileOrderByVersionDesc(profile))
                .thenReturn(List.of(activePlan)); // only 1 plan → no refresh
        when(fitnessPlanRepository.save(any())).thenReturn(new FitnessPlan());

        String json = "{\"ai_notes\":\"Evolution\",\"exercise_refresh\":false,\"sessions\":[]}";
        mockChatResponse(json);

        FitnessPlan result = service.evolvePlan("alice");

        verify(chatModel).call(any(Prompt.class));
        verify(sessionLogRepository).save(argThat(l -> ((FitnessSessionLog) l).isAiAnalyzed()));
    }

    // ===== Exercise refresh detection =====

    @Test
    void evolvePlan_bothPlansRatedTooEasy_refreshInstructionInPrompt() {
        FitnessPlan activePlan = buildActivePlan();

        FitnessPlan prev1 = new FitnessPlan();
        prev1.setId(2L);
        prev1.setVersion(2);
        FitnessPlan prev2 = new FitnessPlan();
        prev2.setId(1L);
        prev2.setVersion(1);

        FitnessPlanSession bwSession1 = sessionForPlan(prev1, SessionType.BODYWEIGHT, 1, 1);
        FitnessPlanSession bwSession2 = sessionForPlan(prev2, SessionType.BODYWEIGHT, 1, 1);

        FitnessSessionLog easyLog1 = completedLog(bwSession1, 2); // rating 2 = too easy
        FitnessSessionLog easyLog2 = completedLog(bwSession2, 2);

        FitnessPlanSession currentSession = sessionForPlan(activePlan, SessionType.BODYWEIGHT, 1, 1);
        FitnessSessionLog currentLog = completedLog(currentSession, 2);
        currentLog.setAiAnalyzed(false);

        when(fitnessPlanRepository.findByFitnessProfileAndActiveTrue(profile)).thenReturn(Optional.of(activePlan));
        when(fitnessPlanSessionRepository.findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(activePlan))
                .thenReturn(List.of(currentSession));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(currentSession)).thenReturn(Optional.of(currentLog));
        when(fitnessPlanRepository.findByFitnessProfileOrderByVersionDesc(profile))
                .thenReturn(List.of(prev1, prev2)); // 2 plans → refresh check

        when(fitnessPlanSessionRepository.findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(prev1))
                .thenReturn(List.of(bwSession1));
        when(fitnessPlanSessionRepository.findByFitnessPlanOrderByWeekNumberAscSessionNumberAsc(prev2))
                .thenReturn(List.of(bwSession2));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(bwSession1)).thenReturn(Optional.of(easyLog1));
        when(sessionLogRepository.findTopByPlanSessionOrderByIdDesc(bwSession2)).thenReturn(Optional.of(easyLog2));

        when(fitnessPlanRepository.save(any())).thenReturn(new FitnessPlan());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        String json = "{\"ai_notes\":\"Refresh\",\"exercise_refresh\":true,\"sessions\":[]}";
        when(chatModel.call(promptCaptor.capture())).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        service.evolvePlan("alice");

        // The evolution prompt should contain the refresh instruction about "leichtere"
        String promptText = promptCaptor.getValue().getContents();
        assertThat(promptText).contains("Ersetze");
    }

    // ===== Helpers =====

    private void mockChatResponse(String content) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    private FitnessPlan buildActivePlan() {
        FitnessPlan plan = new FitnessPlan();
        plan.setId(3L);
        plan.setFitnessProfile(profile);
        plan.setActive(true);
        plan.setVersion(3);
        return plan;
    }

    private FitnessPlanSession sessionForPlan(FitnessPlan plan, SessionType type, int week, int num) {
        FitnessPlanSession s = new FitnessPlanSession();
        s.setId((long) (plan.getId() * 10 + num));
        s.setFitnessPlan(plan);
        s.setWeekNumber(week);
        s.setSessionNumber(num);
        s.setSessionType(type);
        return s;
    }

    private FitnessSessionLog completedLog(FitnessPlanSession session, int rating) {
        FitnessSessionLog log = new FitnessSessionLog();
        log.setStatus(SessionStatus.COMPLETED);
        log.setDifficultyRating(rating);
        log.setPlanSession(session);
        log.setLogDate(LocalDate.now());
        log.setAiAnalyzed(true);
        return log;
    }
}

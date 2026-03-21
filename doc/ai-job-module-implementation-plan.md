# AI-Powered Job Module — Implementation Plan

## Overview

This module enables AI-driven background jobs stored in the database, dynamically scheduled via cron,
executed through a ChatClient call with registered capability tools, and persisted with full run logs
for a maintenance UI. It follows existing project conventions: Lombok entities, JPA repositories in
`dao/`, services in `service/`, Thymeleaf UI under `/admin`, Spring AI tool callbacks, and Liquibase
migrations.

**Example job**: *"Every day at 8 AM, write a summary email to the admin covering the latest
expenses, any open private insurance entries, and noteworthy security rate movements."* — The LLM
receives the instruction, calls the relevant data tools, composes an HTML summary, and sends it via
`sendAdminEmail(...)`.

---

## When to Use This Module vs. the Notification System

These two modules are complementary. Choosing the wrong one adds unnecessary cost or complexity.

| Criterion | AI Job Module | Notification System (evaluators) |
|---|---|---|
| Task type | Synthesis, composition, narration | Condition check, threshold alert |
| Output | Free-form text / HTML email | Structured notification (title + message) |
| Cost per run | LLM API call (non-trivial) | Near-zero (pure Java logic) |
| Determinism | Non-deterministic | Fully deterministic |
| Adding new types | DB-only (no redeploy) | New `@Component` class + redeploy |
| Testability | Harder (mock LLM) | Straightforward unit tests |

**Use the AI job module when:**
- The task requires synthesising data from multiple sources into readable prose (e.g. a daily summary email)
- The output format varies depending on the data (e.g. "highlight only the 3 most unusual movements")
- Authoring flexibility matters more than cost predictability

**Use a notification evaluator when:**
- The task is a deterministic condition check (e.g. "expenses > 80% of budget")
- The result is a pass/fail with a fixed message template
- The job runs frequently (e.g. hourly) — LLM cost per run is prohibitive

**Anti-pattern to avoid:** using an AI job to do something a notification evaluator could do
deterministically (e.g. "check if my budget is exceeded and notify me"). That wastes LLM calls and
makes results unpredictable. Implement it as an evaluator instead.

---

## Step 1 — Database Layer (Entities + Liquibase)

### 1.1 Create `db.changelog_1_28.xml`

Location: `src/main/resources/db/changelog/db.changelog_1_28.xml`

Create two tables:

**`ai_job`**

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `name` | VARCHAR(255) NOT NULL | Display name |
| `description` | TEXT | Human-readable explanation |
| `cron_expression` | VARCHAR(100) NOT NULL | Standard cron (6 fields) |
| `system_prompt` | TEXT NOT NULL | Natural language instruction for the LLM |
| `enabled` | BOOLEAN DEFAULT TRUE | Allows pausing without deletion |
| `last_run_at` | DATETIME | Denormalised for fast UI queries |
| `last_run_status` | VARCHAR(20) | Denormalised: SUCCESS / FAILURE / RUNNING |
| `created_at` | DATETIME NOT NULL | |
| `last_modified_at` | DATETIME NOT NULL | |

**`ai_job_run_log`**

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `job_id` | BIGINT FK → `ai_job(id)` | |
| `started_at` | DATETIME NOT NULL | |
| `finished_at` | DATETIME | NULL while running |
| `status` | VARCHAR(20) NOT NULL | SUCCESS / FAILURE / RUNNING |
| `ai_response` | TEXT | Final LLM message content |
| `tool_calls_json` | LONGTEXT | JSON array of tool calls (see schema below) |
| `error_message` | TEXT | Populated on FAILURE |

`tool_calls_json` element schema:
```json
{
  "tool": "getExpenseSummary",
  "input": "{\"year\":2026,\"month\":3}",
  "output": "[...]",
  "durationMs": 95,
  "error": null
}
```

Register the changelog in `db.changelog-master.xml`.

### 1.2 Create Entity Classes

**`entity/job/AiJob.java`**
- `@Entity`, `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- `@OneToMany(mappedBy = "job", cascade = CascadeType.ALL)` → `List<AiJobRunLog> runLogs`
- `@Enumerated(EnumType.STRING)` for `lastRunStatus` using a new `JobRunStatus` enum

**`entity/job/AiJobRunLog.java`**
- `@Entity`, `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- `@ManyToOne @JoinColumn(name = "job_id")` → `AiJob job`
- `@Enumerated(EnumType.STRING)` for `status`

**`entity/job/JobRunStatus.java`** (enum)
```java
public enum JobRunStatus { RUNNING, SUCCESS, FAILURE }
```

### 1.3 Create DTO Classes

**`dto/job/AiJobDTO.java`** — used for create/update requests and list responses:
- `id`, `name`, `description`, `cronExpression`, `systemPrompt`, `enabled`,
  `lastRunAt`, `lastRunStatus`

**`dto/job/AiJobRunLogDTO.java`** — used for the log detail view:
- `id`, `jobId`, `startedAt`, `finishedAt`, `status`, `aiResponse`,
  `List<ToolCallRecord> toolCalls`, `errorMessage`

**`dto/job/ToolCallRecord.java`** — deserialized from `toolCallsJson`:
- `tool`, `input`, `output`, `durationMs`, `error`

### 1.4 Create Repository Interfaces

**`dao/job/AiJobRepository.java`**
```java
public interface AiJobRepository extends JpaRepository<AiJob, Long> {
    List<AiJob> findByEnabledTrue();
}
```

**`dao/job/AiJobRunLogRepository.java`**
```java
public interface AiJobRunLogRepository extends JpaRepository<AiJobRunLog, Long> {
    Optional<AiJobRunLog> findTopByJobOrderByStartedAtDesc(AiJob job);
}
```

---

## Step 2 — Capability Tools (the AI's "hands")

### 2.1 Create `service/job/JobToolProvider.java`

A `@Component` with `@Tool`-annotated methods. This is the complete registry of actions available
to the LLM during a job run. Adding a new capability is a single annotated method here — no other
changes needed.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JobToolProvider {

    private final EmailService emailService;
    private final FinanceService financeService;
    private final ExpensesService expensesService;
    // ... inject other services as needed

    @Tool(description = "Send an HTML email to the admin. Use for alerts and summaries.")
    public String sendAdminEmail(String subject, String htmlBody) { ... }

    @Tool(description = """
        Returns all active owned securities with: name, ISIN, quantity, current price,
        currency, and priceChangePercent (e.g. 0.053 means +5.3%, -0.02 means -2%).
        """)
    public List<Map<String, Object>> getOwnedSecurities() { ... }

    @Tool(description = "Returns total portfolio value, total gain/loss, and breakdown by type (SHARE, ETF, FUND).")
    public Map<String, Object> getPortfolioSummary() { ... }

    @Tool(description = "Returns expense totals for the given year and month, grouped by category.")
    public Map<String, Object> getExpenseSummary(int year, int month) { ... }

    @Tool(description = "Returns private insurance entries that are pending action or awaiting a document.")
    public List<Map<String, Object>> getOpenInsuranceEntries() { ... }

    @Tool(description = "Returns the current balance sheet snapshot with total assets and liabilities.")
    public Map<String, Object> getBalanceSummary() { ... }

    @Tool(description = "Returns today's date as an ISO string (yyyy-MM-dd). Use to determine the current date.")
    public String getCurrentDate() { return LocalDate.now().toString(); }
}
```

**Implementation notes:**
- `sendAdminEmail` delegates to `EmailService.sendHtmlMessage(adminEmail, subject, body)`
- `getOwnedSecurities` calls `FinanceService.retrieveFinancialProducts()` and maps to plain
  `Map<String, Object>` for safe JSON serialisation by the Spring AI framework
- Return types must be serialisable to JSON — avoid returning JPA entities directly

---

## Step 3 — Tool Call Interceptor & AI Job Executor

### 3.1 Create `service/job/ToolCallInterceptor.java`

A utility class that wraps a Spring AI `ToolCallback` to record timing and inputs/outputs:

```java
public class ToolCallInterceptor {
    // ThreadLocal list accumulating ToolCallRecord entries for the current execution
    private static final ThreadLocal<List<ToolCallRecord>> TRACKER = ThreadLocal.withInitial(ArrayList::new);

    public static void clear() { TRACKER.get().clear(); }
    public static List<ToolCallRecord> getRecords() { return List.copyOf(TRACKER.get()); }

    public static ToolCallback wrap(ToolCallback delegate) {
        return new InterceptingToolCallback(delegate, TRACKER.get());
    }
}
```

`InterceptingToolCallback` records: tool name, raw input string, start time → calls delegate →
records output string and duration, catches and records any exception.

### 3.2 Create `service/job/AiJobExecutorService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AiJobExecutorService {

    private final ChatModel chatModel;
    private final JobToolProvider jobToolProvider;
    private final AiJobRepository jobRepository;
    private final AiJobRunLogRepository runLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${ai.jobs.timeout-seconds:120}")
    private int timeoutSeconds;

    @Async("aiJobExecutor")
    public void execute(AiJob job) {
        // 1. Create RUNNING log entry
        AiJobRunLog log = runLogRepository.save(AiJobRunLog.builder()
            .job(job).startedAt(LocalDateTime.now()).status(JobRunStatus.RUNNING).build());

        // Update job's last run fields
        job.setLastRunAt(log.getStartedAt());
        job.setLastRunStatus(JobRunStatus.RUNNING);
        jobRepository.save(job);

        ToolCallInterceptor.clear();
        String aiResponse = null;
        JobRunStatus finalStatus = JobRunStatus.FAILURE;
        String errorMessage = null;

        try {
            // 2. Build tool callbacks with interceptor wrapping
            ToolCallback[] callbacks = Arrays.stream(ToolCallbacks.from(jobToolProvider))
                .map(ToolCallInterceptor::wrap)
                .toArray(ToolCallback[]::new);

            // 3. Build system prompt
            String systemPrompt = """
                You are Plan-Man's automated job agent. Today is %s.
                Use the available tools to fulfil your task.
                After completing the task, write a brief summary of what you did and what actions you took.
                """.formatted(LocalDate.now());

            // 4. Execute AI call with timeout
            aiResponse = CompletableFuture.supplyAsync(() ->
                ChatClient.builder(chatModel).build()
                    .prompt()
                    .system(systemPrompt)
                    .user(job.getSystemPrompt())
                    .toolCallbacks(callbacks)
                    .call()
                    .content()
            ).get(timeoutSeconds, TimeUnit.SECONDS);

            finalStatus = JobRunStatus.SUCCESS;

        } catch (TimeoutException e) {
            errorMessage = "Job timed out after " + timeoutSeconds + " seconds";
            log.error("AI job '{}' timed out", job.getName());
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("AI job '{}' failed: {}", job.getName(), e.getMessage(), e);
        }

        // 5. Persist completed log
        List<ToolCallRecord> toolCalls = ToolCallInterceptor.getRecords();
        log.setFinishedAt(LocalDateTime.now());
        log.setStatus(finalStatus);
        log.setAiResponse(aiResponse);
        log.setToolCallsJson(objectMapper.writeValueAsString(toolCalls));
        log.setErrorMessage(errorMessage);
        runLogRepository.save(log);

        // 6. Update denormalised fields on job
        job.setLastRunStatus(finalStatus);
        jobRepository.save(job);
    }
}
```

Register an `@Bean("aiJobExecutor")` `ThreadPoolTaskExecutor` with pool size from config.

---

## Step 4 — Dynamic Scheduler

### 4.1 Create `conf/AiJobSchedulerConfig.java`

```java
@Configuration
public class AiJobSchedulerConfig {

    @Bean("aiJobTaskScheduler")
    public ThreadPoolTaskScheduler aiJobTaskScheduler(
            @Value("${ai.jobs.thread-pool-size:3}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("ai-job-");
        scheduler.setErrorHandler(t -> log.error("AI job scheduler error", t));
        scheduler.initialize();
        return scheduler;
    }
}
```

### 4.2 Create `service/job/AiJobSchedulerService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AiJobSchedulerService {

    @Qualifier("aiJobTaskScheduler")
    private final ThreadPoolTaskScheduler taskScheduler;
    private final AiJobRepository jobRepository;
    private final AiJobExecutorService executorService;

    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    @PostConstruct
    public void initJobs() {
        jobRepository.findByEnabledTrue().forEach(this::scheduleJob);
        log.info("Scheduled {} AI jobs", scheduledFutures.size());
    }

    public void scheduleJob(AiJob job) {
        cancelJob(job.getId());
        if (!job.isEnabled()) return;
        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> executorService.execute(job),
            new CronTrigger(job.getCronExpression())
        );
        scheduledFutures.put(job.getId(), future);
        log.info("Scheduled AI job '{}' with cron '{}'", job.getName(), job.getCronExpression());
    }

    public void cancelJob(Long jobId) {
        ScheduledFuture<?> existing = scheduledFutures.remove(jobId);
        if (existing != null) existing.cancel(false);
    }
}
```

---

## Step 5 — Service Layer (CRUD + Run Management)

### 5.1 Create `service/job/AiJobService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AiJobService {

    private final AiJobRepository jobRepository;
    private final AiJobRunLogRepository runLogRepository;
    private final AiJobSchedulerService schedulerService;
    private final AiJobExecutorService executorService;
    private final ObjectMapper objectMapper;

    public List<AiJobDTO> findAll() { ... }       // returns all jobs mapped to DTOs

    public AiJobDTO findById(Long id) { ... }     // throws if not found

    public AiJobDTO create(AiJobDTO dto) {
        AiJob job = jobRepository.save(mapToEntity(dto));
        schedulerService.scheduleJob(job);
        return mapToDTO(job);
    }

    public AiJobDTO update(Long id, AiJobDTO dto) {
        AiJob job = getJobOrThrow(id);
        // update fields from dto
        job = jobRepository.save(job);
        schedulerService.scheduleJob(job);   // re-schedule with new cron
        return mapToDTO(job);
    }

    public void delete(Long id) {
        schedulerService.cancelJob(id);
        jobRepository.deleteById(id);
    }

    public void setEnabled(Long id, boolean enabled) {
        AiJob job = getJobOrThrow(id);
        job.setEnabled(enabled);
        jobRepository.save(job);
        if (enabled) schedulerService.scheduleJob(job);
        else schedulerService.cancelJob(id);
    }

    public void triggerNow(Long id) {
        executorService.execute(getJobOrThrow(id));  // async via @Async
    }

    public AiJobRunLogDTO getLastLog(Long jobId) {
        AiJob job = getJobOrThrow(jobId);
        return runLogRepository.findTopByJobOrderByStartedAtDesc(job)
            .map(this::mapLogToDTO)
            .orElseThrow(() -> new RuntimeException("No run log found for job " + jobId));
    }
}
```

---

## Step 6 — API Controller

### 6.1 Create `controller/api/AiJobApiController.java`

```java
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class AiJobApiController {

    private final AiJobService jobService;

    @GetMapping
    public List<AiJobDTO> list() { return jobService.findAll(); }

    @GetMapping("/{id}")
    public AiJobDTO get(@PathVariable Long id) { return jobService.findById(id); }

    @PostMapping
    public ResponseEntity<AiJobDTO> create(@RequestBody AiJobDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.create(dto));
    }

    @PutMapping("/{id}")
    public AiJobDTO update(@PathVariable Long id, @RequestBody AiJobDTO dto) {
        return jobService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<Void> triggerNow(@PathVariable Long id) {
        jobService.triggerNow(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable Long id) {
        jobService.setEnabled(id, true);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        jobService.setEnabled(id, false);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/last-log")
    public AiJobRunLogDTO lastLog(@PathVariable Long id) { return jobService.getLastLog(id); }
}
```

---

## Step 7 — UI Controller & Thymeleaf Template

### 7.1 Create `controller/ui/AiJobController.java`

```java
@Controller
@RequestMapping("/admin/jobs")
@RequiredArgsConstructor
public class AiJobController {

    private final AiJobService jobService;

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("jobs", jobService.findAll());
        model.addAttribute("capabilities", List.of(
            "sendAdminEmail(subject, body) — Send HTML email to the admin",
            "getOwnedSecurities() — All active positions with price change %",
            "getPortfolioSummary() — Total value and gain/loss by asset type",
            "getExpenseSummary(year, month) — Monthly expenses by category",
            "getOpenInsuranceEntries() — Insurance entries pending action or documents",
            "getBalanceSummary() — Current balance sheet snapshot",
            "getCurrentDate() — Today's date as ISO string"
        ));
        return "jobs/overview";
    }
}
```

Secured automatically by the existing `SecurityConfig` rule: `/admin/**` requires `ADMIN` role.

### 7.2 Create `templates/jobs/overview.html`

**Main table:**

| Column | Notes |
|---|---|
| Name | Job name |
| Cron | Expression (e.g. `0 0 8 * * *`) |
| Enabled | Toggle switch (POST to `/api/jobs/{id}/enable` or `/disable`) |
| Last Run | Formatted datetime, "Never" if null |
| Last Status | Badge: green SUCCESS / red FAILURE / grey RUNNING / — |
| Actions | Run Now · View Log · Edit · Delete |

**Create / Edit modal** (single shared modal, pre-filled on edit):
- Name (text input)
- Description (text input)
- Cron Expression (text input with format hint)
- System Prompt (large `<textarea>` — the AI instruction)
- Enabled (checkbox)

**Log Detail modal** (opened by "View Log"):
- Fetches `GET /api/jobs/{id}/last-log` via fetch/AJAX
- Displays: Started · Finished · Duration · Status badge
- Tool Call Timeline: each call shows tool name, collapsible input/output JSON, duration pill
- AI Response: rendered as preformatted text
- Error Message: shown in red if present

**Capabilities Reference** (collapsible section at page bottom):
- Lists all available tool methods with their descriptions
- Helps users write effective system prompts

---

## Step 8 — Configuration

### 8.1 Add to `application.properties`

```properties
# AI Job Module
ai.jobs.thread-pool-size=3
ai.jobs.timeout-seconds=120
# Uses spring.ai.openai.chat.options.model by default; override here if needed
# ai.jobs.model=gpt-4o-mini
```

### 8.2 Register `@Async` executor bean in `conf/AiJobSchedulerConfig.java`

```java
@Bean("aiJobExecutor")
public TaskExecutor aiJobExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setThreadNamePrefix("ai-job-exec-");
    executor.initialize();
    return executor;
}
```

Enable `@Async` on the main application class or a `@Configuration` class with `@EnableAsync`.

---

## Step 9 — Tests

Follow existing project patterns to maintain ≥ 90% Jacoco line coverage.

### 9.1 `JobToolProviderTest`
- `@ExtendWith(MockitoExtension.class)` + `@InjectMocks`
- Mock `FinanceService`, `EmailService`, etc.
- Test each tool method: verify correct delegation, correct return shape, null/empty cases

### 9.2 `AiJobExecutorServiceTest`
- Mock `ChatModel` (return fixed content string), `JobToolProvider`, repos, `ObjectMapper`
- Test: SUCCESS path → log saved with correct status and ai_response
- Test: FAILURE path (exception thrown) → log saved with FAILURE and errorMessage
- Test: Timeout path → log saved with FAILURE and timeout message

### 9.3 `AiJobSchedulerServiceTest`
- Mock `ThreadPoolTaskScheduler`, `AiJobRepository`, `AiJobExecutorService`
- Test `initJobs()`: verifies `scheduleJob` called for each enabled job
- Test `scheduleJob()`: verifies `taskScheduler.schedule()` called with correct cron
- Test `cancelJob()`: verifies future is cancelled and removed from map
- Test `scheduleJob()` on already-scheduled job: verifies old future cancelled before new one registered

### 9.4 `AiJobServiceTest`
- Mock repos, scheduler service, executor service
- Test `create()`: entity saved + scheduler notified
- Test `update()`: entity updated + scheduler rescheduled
- Test `delete()`: scheduler cancelled + entity deleted
- Test `setEnabled(false)`: scheduler cancelled
- Test `setEnabled(true)`: scheduler notified
- Test `triggerNow()`: executor service called
- Test `getLastLog()`: maps log entity correctly, throws when no log found

### 9.5 `AiJobApiControllerTest`
- `@WebMvcTest(AiJobApiController.class)` + `@MockitoBean AiJobService`
- Test all endpoints: correct HTTP status codes, correct delegation to service
- Test `POST /{id}/run` returns `202 Accepted`
- Test `DELETE /{id}` returns `204 No Content`

### 9.6 Jacoco Exclusions (if needed)
- No new Lombok POJOs are currently excluded unless they are pure `@Data` entities with no logic

---

## Implementation Order

```
Step 1  →  DB schema (Liquibase + entities + repos + DTOs)   ← no risk, pure data layer
Step 2  →  JobToolProvider with sendAdminEmail + getExpenseSummary + getOpenInsuranceEntries + getOwnedSecurities
Step 3  →  ToolCallInterceptor + AiJobExecutorService
Step 4  →  AiJobSchedulerConfig + AiJobSchedulerService
Step 5  →  AiJobService (CRUD + run management)
Step 6  →  AiJobApiController (REST endpoints)
Step 7  →  AiJobController (UI) + Thymeleaf template
Step 8  →  Configuration properties + @Async setup
Step 9  →  Tests (target: maintain ≥ 90% line coverage)
Step 10 →  Remaining JobToolProvider methods (expenses, balance, portfolio summary)
```

---

## End-to-End Data Flow (Daily Summary Email Example)

```
08:00:00  CronTrigger fires (AiJobSchedulerService)
     │
     ▼  AiJobExecutorService.execute(job)
     │   - Creates AiJobRunLog(status=RUNNING)
     │   - Wraps JobToolProvider callbacks with ToolCallInterceptor
     │
     ▼  ChatClient.builder(chatModel).build()
     │   .system("You are Plan-Man's automated job agent. Today is 2026-03-10...")
     │   .user("Write a daily summary email to the admin covering:
     │           1) latest expenses for the current month,
     │           2) open private insurance entries,
     │           3) noteworthy security rate movements.
     │           Compose a well-structured HTML email and send it.")
     │   .toolCallbacks([wrapped callbacks])
     │   .call().content()
     │
     ▼  LLM calls getExpenseSummary(2026, 3)
     │   → ExpenseService returns category totals for March 2026
     │   → Interceptor records: {tool, input, output, durationMs: 95}
     │
     ▼  LLM calls getOpenInsuranceEntries()
     │   → InsuranceService returns entries pending action
     │   → Interceptor records: {tool, input, output, durationMs: 60}
     │
     ▼  LLM calls getOwnedSecurities()
     │   → FinanceService returns positions with price change %
     │   → Interceptor records: {tool, input, output, durationMs: 110}
     │
     ▼  LLM composes HTML email and calls sendAdminEmail(
     │     "Plan-Man Tageszusammenfassung – 10.03.2026",
     │     "<h2>Ausgaben März</h2>...<h2>Offene Versicherungen</h2>...<h2>Wertpapiere</h2>..."
     │   )
     │   → EmailService.sendHtmlMessage(...)
     │   → Interceptor records: {tool, input, output, durationMs: 80}
     │
     ▼  LLM returns: "Daily summary email sent. Covered 5 expense categories,
     │                2 open insurance entries, and 8 securities (2 with >3% movement)."
     │
     ▼  AiJobExecutorService persists AiJobRunLog:
         status        = SUCCESS
         aiResponse    = "Daily summary email sent. Covered 5 expense categories..."
         toolCallsJson = [{tool:"getExpenseSummary",       durationMs:95,  ...},
                          {tool:"getOpenInsuranceEntries", durationMs:60,  ...},
                          {tool:"getOwnedSecurities",      durationMs:110, ...},
                          {tool:"sendAdminEmail",          durationMs:80,  ...}]
         finishedAt    = 08:00:04
```

---

## File Structure Summary

```
src/main/java/at/v3rtumnus/planman/
├── conf/
│   └── AiJobSchedulerConfig.java         (TaskScheduler + TaskExecutor beans)
├── controller/
│   ├── api/
│   │   └── AiJobApiController.java
│   └── ui/
│       └── AiJobController.java
├── dao/
│   └── job/
│       ├── AiJobRepository.java
│       └── AiJobRunLogRepository.java
├── dto/
│   └── job/
│       ├── AiJobDTO.java
│       ├── AiJobRunLogDTO.java
│       └── ToolCallRecord.java
├── entity/
│   └── job/
│       ├── AiJob.java
│       ├── AiJobRunLog.java
│       └── JobRunStatus.java
└── service/
    └── job/
        ├── AiJobService.java
        ├── AiJobExecutorService.java
        ├── AiJobSchedulerService.java
        ├── JobToolProvider.java
        └── ToolCallInterceptor.java

src/main/resources/
├── db/changelog/
│   └── db.changelog_1_28.xml
└── templates/
    └── jobs/
        └── overview.html

src/test/java/at/v3rtumnus/planman/
└── service/job/
    ├── AiJobServiceTest.java
    ├── AiJobExecutorServiceTest.java
    ├── AiJobSchedulerServiceTest.java
    └── JobToolProviderTest.java
└── controller/api/
    └── AiJobApiControllerTest.java
```

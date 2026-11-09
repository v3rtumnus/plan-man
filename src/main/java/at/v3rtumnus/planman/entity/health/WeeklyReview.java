package at.v3rtumnus.planman.entity.health;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One week (Monday to Sunday) of one user: the numbers, Opus' feedback and, every 4 weeks, the
 * goal check. Every column is named explicitly: several fields end in a single capital
 * (proteinAvgG), which the naming strategy would otherwise map to "protein_avgg".
 */
@Entity
@Table(name = "WEEKLY_REVIEW")
@Data
@NoArgsConstructor
public class WeeklyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;

    @Column(name = "WEEK_START")
    private LocalDate weekStart;

    /** Days with at least one meal, pauses left out. */
    @Column(name = "LOGGED_DAYS")
    private int loggedDays;

    @Column(name = "PAUSED_DAYS")
    private int pausedDays;

    @Column(name = "KCAL_AVG")
    private Integer kcalAvg;

    /** Average day target of the logged days, training included. */
    @Column(name = "KCAL_TARGET_AVG")
    private Integer kcalTargetAvg;

    @Column(name = "PROTEIN_AVG_G")
    private BigDecimal proteinAvgG;

    @Column(name = "PROTEIN_TARGET_G")
    private Integer proteinTargetG;

    /** Logged days that reached the protein target. */
    @Column(name = "PROTEIN_DAYS")
    private int proteinDays;

    @Column(name = "FREELETICS_SESSIONS")
    private int freeleticsSessions;

    @Column(name = "FOOTBALL_PLAYED")
    private boolean footballPlayed;

    @Column(name = "TRAINING_KCAL")
    private int trainingKcal;

    /** 7-day weight trend on Sunday. */
    @Column(name = "WEIGHT_TREND_KG")
    private BigDecimal weightTrendKg;

    /** Trend on Sunday minus trend on the Sunday before. */
    @Column(name = "WEIGHT_CHANGE_KG")
    private BigDecimal weightChangeKg;

    /** Slope over the last 3 weeks, per week. */
    @Column(name = "WEEKLY_RATE_KG")
    private BigDecimal weeklyRateKg;

    /** Latest waist value up to Sunday. */
    @Column(name = "WAIST_CM")
    private BigDecimal waistCm;

    /** Change against the waist value at the goal's start. */
    @Column(name = "WAIST_CHANGE_CM")
    private BigDecimal waistChangeCm;

    @Column(name = "PUSH_UPS")
    private Integer pushUps;

    @Column(name = "GOAL_CHECK")
    private boolean goalCheck;

    @Enumerated(EnumType.STRING)
    @Column(name = "GOAL_STATUS")
    private GoalStatus goalStatus;

    @Column(name = "HEADLINE")
    private String headline;

    @Column(name = "FEEDBACK", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "GOAL_REASON", columnDefinition = "TEXT")
    private String goalReason;

    @Column(name = "PROPOSED_KCAL_TARGET")
    private Integer proposedKcalTarget;

    @Column(name = "PROPOSED_PROTEIN_TARGET_G")
    private Integer proposedProteinTargetG;

    @Column(name = "PROPOSED_TARGET_DATE")
    private LocalDate proposedTargetDate;

    @Column(name = "PROPOSAL_REASON", columnDefinition = "TEXT")
    private String proposalReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "FEEDBACK_STATUS")
    private FeedbackStatus feedbackStatus = FeedbackStatus.PENDING;

    @Column(name = "MODEL")
    private String model;

    @Column(name = "FEEDBACK_AT")
    private LocalDateTime feedbackAt;

    @Column(name = "ERROR")
    private String error;

    @Enumerated(EnumType.STRING)
    @Column(name = "DECISION")
    private ReviewDecision decision;

    @Column(name = "DECIDED_AT")
    private LocalDateTime decidedAt;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public LocalDate weekEnd() {
        return weekStart.plusDays(6);
    }

    public boolean hasProposal() {
        return proposedKcalTarget != null || proposedProteinTargetG != null || proposedTargetDate != null;
    }

    /** Goal check says a conversation is worth it: too fast or stalled. */
    public boolean needsTalk() {
        return goalCheck && goalStatus != null && goalStatus.needsTalk();
    }
}

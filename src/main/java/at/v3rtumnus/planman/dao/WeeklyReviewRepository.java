package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.WeeklyReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, Long> {

    Optional<WeeklyReview> findByUserUsernameAndWeekStart(String username, LocalDate weekStart);

    Optional<WeeklyReview> findByIdAndUserUsername(Long id, String username);

    Optional<WeeklyReview> findFirstByUserUsernameOrderByWeekStartDesc(String username);

    Optional<WeeklyReview> findFirstByUserUsernameAndGoalCheckTrueOrderByWeekStartDesc(String username);

    List<WeeklyReview> findTop12ByUserUsernameOrderByWeekStartDesc(String username);

    /** Reviews whose text arrived recently: the notification rules look at these. */
    List<WeeklyReview> findByFeedbackAtAfter(LocalDateTime since);
}

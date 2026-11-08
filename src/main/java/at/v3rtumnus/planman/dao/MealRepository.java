package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.Meal;
import at.v3rtumnus.planman.entity.health.MealSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MealRepository extends JpaRepository<Meal, Long> {

    List<Meal> findByUserUsernameAndEatenOnOrderByCreatedAtAsc(String username, LocalDate eatenOn);

    List<Meal> findByUserUsernameAndEatenOnAndSlotOrderByCreatedAtAsc(String username, LocalDate eatenOn, MealSlot slot);

    Optional<Meal> findByIdAndUserUsername(Long id, String username);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Meal m SET m.savedMeal = null WHERE m.savedMeal.id = :savedMealId")
    void detachSavedMeal(@Param("savedMealId") Long savedMealId);
}

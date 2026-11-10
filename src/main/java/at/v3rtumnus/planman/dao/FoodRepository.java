package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.Food;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, String> {
}

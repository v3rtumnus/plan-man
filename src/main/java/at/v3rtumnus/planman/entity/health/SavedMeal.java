package at.v3rtumnus.planman.entity.health;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A favourite for "Schnell wieder"; logging it copies the items into a new meal. */
@Entity
@Table(name = "SAVED_MEAL")
@Getter
@Setter
@NoArgsConstructor
public class SavedMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;

    private String name;

    @Enumerated(EnumType.STRING)
    private MealSlot usualSlot;

    private int useCount;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "savedMeal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<SavedMealItem> items = new ArrayList<>();

    public void replaceItems(List<SavedMealItem> newItems) {
        items.clear();
        int position = 0;
        for (SavedMealItem item : newItems) {
            item.setSavedMeal(this);
            item.setPosition(position++);
            items.add(item);
        }
    }

    public int kcal() {
        return items.stream().mapToInt(SavedMealItem::kcal).sum();
    }

    public BigDecimal protein() {
        return items.stream().map(SavedMealItem::protein).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

package at.v3rtumnus.planman.entity.health;

import at.v3rtumnus.planman.entity.UserProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One logged meal; kcal and macros are the sum of its items, kept for fast day totals. */
@Entity
@Table(name = "MEAL")
@Getter
@Setter
@NoArgsConstructor
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;

    private LocalDate eatenOn;

    @Enumerated(EnumType.STRING)
    private MealSlot slot;

    private String title;

    @Enumerated(EnumType.STRING)
    private MealSource source;

    private int kcal;
    @Column(name = "PROTEIN_G")
    private BigDecimal proteinG;
    @Column(name = "CARBS_G")
    private BigDecimal carbsG;
    @Column(name = "FAT_G")
    private BigDecimal fatG;

    @ManyToOne
    @JoinColumn(name = "SAVED_MEAL_ID")
    private SavedMeal savedMeal;

    @ManyToOne
    @JoinColumn(name = "MEAL_RECOGNITION_ID")
    private MealRecognition recognition;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "meal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<MealItem> items = new ArrayList<>();

    /** Replaces the items and recomputes the totals. */
    public void replaceItems(List<MealItem> newItems) {
        items.clear();
        int position = 0;
        for (MealItem item : newItems) {
            item.setMeal(this);
            item.setPosition(position++);
            items.add(item);
        }
        kcal = items.stream().mapToInt(MealItem::kcal).sum();
        proteinG = items.stream().map(MealItem::protein).reduce(BigDecimal.ZERO, BigDecimal::add);
        carbsG = items.stream().map(MealItem::carbs).reduce(BigDecimal.ZERO, BigDecimal::add);
        fatG = items.stream().map(MealItem::fat).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

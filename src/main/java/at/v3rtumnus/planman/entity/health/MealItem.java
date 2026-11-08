package at.v3rtumnus.planman.entity.health;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "MEAL_ITEM")
@Getter
@Setter
@NoArgsConstructor
public class MealItem implements FoodValues {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "MEAL_ID")
    private Meal meal;

    private int position;
    private String name;
    private int grams;
    @Column(name = "KCAL_PER_100G")
    private BigDecimal kcalPer100g;
    @Column(name = "PROTEIN_PER_100G")
    private BigDecimal proteinPer100g;
    @Column(name = "CARBS_PER_100G")
    private BigDecimal carbsPer100g;
    @Column(name = "FAT_PER_100G")
    private BigDecimal fatPer100g;
    /** The amount was estimated (e.g. from a photo) and should be checked. */
    private boolean unsure;
    private String portionHint;
}

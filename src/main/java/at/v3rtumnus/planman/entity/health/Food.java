package at.v3rtumnus.planman.entity.health;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A packaged product by barcode, per 100 g (H7). */
@Entity
@Table(name = "FOOD")
@Data
@NoArgsConstructor
public class Food {

    public enum Source { OFF, USER }

    @Id
    private String barcode;

    private String name;

    private String brand;

    @Column(name = "KCAL_PER_100G")
    private BigDecimal kcalPer100g;

    @Column(name = "PROTEIN_PER_100G")
    private BigDecimal proteinPer100g;

    @Column(name = "CARBS_PER_100G")
    private BigDecimal carbsPer100g;

    @Column(name = "FAT_PER_100G")
    private BigDecimal fatPer100g;

    @Column(name = "SERVING_G")
    private Integer servingG;

    private String servingHint;

    @Enumerated(EnumType.STRING)
    private Source source;

    private LocalDateTime updatedAt;
}

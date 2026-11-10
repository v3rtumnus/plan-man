package at.v3rtumnus.planman.entity.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** EUR price of a position at its last Kursbewegung notification (first anchor: first evaluation). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class PriceAnchor {

    @Id
    private String isin;

    private LocalDate anchorDate;

    @Column(name = "anchor_price_eur")
    private BigDecimal anchorPriceEur;

    private LocalDate lastNotifiedOn;
}

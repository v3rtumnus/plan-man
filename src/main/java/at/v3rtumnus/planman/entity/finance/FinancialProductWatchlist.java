package at.v3rtumnus.planman.entity.finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FinancialProductWatchlist {

    private String isin;

    @Enumerated(EnumType.STRING)
    private FinancialProductType type;
}

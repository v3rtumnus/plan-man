package at.v3rtumnus.planman.dto.credit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreditOverview {
    private LocalDate originalPlanLastDate;
    private LocalDate currentPlanLastDate;

    private List<CreditPlanRow> additionalPayments;

    private int minimumInstallment;
}

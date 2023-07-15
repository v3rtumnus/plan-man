package at.v3rtumnus.planman.dto.finance;

import at.v3rtumnus.planman.entity.finance.*;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToOne;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class SavingsPlanDto {
    private LocalDate startDate;
    private LocalDate endDate;

    private Interval interval;

    private BigDecimal amount;

    private FinancialProductDTO financialProduct;

    public static SavingsPlanDto fromEntity(SavingsPlan savingsPlan) {
        SavingsPlanDto savingsPlanDto = new SavingsPlanDto();
        BeanUtils.copyProperties(savingsPlan, savingsPlanDto);

        savingsPlanDto.setFinancialProduct(FinancialProductDTO.fromEntity(savingsPlan.getFinancialProduct()));

        return savingsPlanDto;
    }
}

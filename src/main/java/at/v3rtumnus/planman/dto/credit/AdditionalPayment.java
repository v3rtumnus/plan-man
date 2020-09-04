package at.v3rtumnus.planman.dto.credit;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdditionalPayment {
    private LocalDate paymentDate;
    private BigDecimal paymentAmount;
}

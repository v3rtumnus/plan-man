package at.v3rtumnus.planman.dto.expenses;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class Expense {
    private BigDecimal amount;
    private String category;
    private String comment;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
}

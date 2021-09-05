package at.v3rtumnus.planman.dto.expense;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExpenseGraphItem {

    private String category;
    private List<BigDecimal> amounts = new ArrayList<>();
    private String color;

    public ExpenseGraphItem(String category) {
        this.category = category;
    }
}

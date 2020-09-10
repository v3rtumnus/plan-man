package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.OngoingExpensesRepository;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpenseService {

    @Autowired
    private OngoingExpensesRepository ongoingExpensesRepository;

    public LocalDate getMinimumExpenseDate() {
        return ongoingExpensesRepository.findOldestOngoingExpenseDate();
    }

    public List<ExpenseSummary> getExpenseSummaryForMonth(int year, int month) {
        return ongoingExpensesRepository.getExpenseSummaryForMonth(year, month);
    }

    public Map<LocalDate, List<ExpenseSummary>> getExpenseSummariesForLastMonths(Integer lastMonths) {
        Map<LocalDate, List<ExpenseSummary>> expenseSummaries = new HashMap<>();
        LocalDate currentDate;

        if (lastMonths != null) {
            currentDate = LocalDate.now().minusMonths(lastMonths - 1).withDayOfMonth(1);
        } else {
            currentDate = getMinimumExpenseDate().withDayOfMonth(1);
        }

        while (currentDate.isBefore(LocalDate.now())) {
            expenseSummaries.put(currentDate,
                    ongoingExpensesRepository.getExpenseSummaryForMonth(currentDate.getYear(), currentDate.getMonthValue()));

            currentDate = currentDate.plusMonths(1);
        }

        return expenseSummaries;
    }
}

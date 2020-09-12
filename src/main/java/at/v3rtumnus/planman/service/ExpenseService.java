package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.ExpenseCategoryRepository;
import at.v3rtumnus.planman.dao.ExpenseRepository;
import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dto.expense.ExpenseDto;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.entity.Expense;
import at.v3rtumnus.planman.entity.ExpenseCategory;
import at.v3rtumnus.planman.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    public LocalDate getMinimumExpenseDate() {
        return expenseRepository.findOldestExpenseDate();
    }

    public List<ExpenseSummary> getExpenseSummaryForMonth(int year, int month) {
        return expenseRepository.getExpenseSummaryForMonth(year, month);
    }

    public Map<LocalDate, List<ExpenseSummary>> getExpenseSummariesForLastMonths(Integer lastMonths) {
        Map<LocalDate, List<ExpenseSummary>> expenseSummaries = new HashMap<>();
        LocalDate currentDate;

        if (lastMonths != null) {
            currentDate = LocalDate.now().minusMonths(lastMonths).withDayOfMonth(1);
        } else {
            currentDate = getMinimumExpenseDate().withDayOfMonth(1);
        }

        while (currentDate.isBefore(LocalDate.now().minusMonths(1))) {
            expenseSummaries.put(currentDate,
                    expenseRepository.getExpenseSummaryForMonth(currentDate.getYear(), currentDate.getMonthValue()));

            currentDate = currentDate.plusMonths(1);
        }

        return expenseSummaries;
    }

    public List<ExpenseCategory> getExpenseCategories() {
        return expenseCategoryRepository.findAll();
    }

    public void saveExpense(ExpenseDto expense) {
        log.info("Saving expense for category {}", expense.getCategory());

        ExpenseCategory category = expenseCategoryRepository.findByName(expense.getCategory())
                .orElseGet(() -> {
                    log.info("Creating new category for {}", expense.getCategory());

                    ExpenseCategory newCategory = new ExpenseCategory(null, expense.getCategory());

                    return expenseCategoryRepository.saveAndFlush(newCategory);
                });

        UserProfile user = userProfileRepository.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new UsernameNotFoundException("Username could not be found"));

        expenseRepository.saveAndFlush(new Expense(null, expense.getDate(), expense.getComment(), expense.getAmount(), category, user));

        log.info("Expense successfully saved");
    }
}

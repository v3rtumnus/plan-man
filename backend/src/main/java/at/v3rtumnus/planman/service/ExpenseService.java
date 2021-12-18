package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.ExpenseCategoryRepository;
import at.v3rtumnus.planman.dao.ExpenseRepository;
import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseSummaryDTO;
import at.v3rtumnus.planman.entity.expense.Expense;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    public Expense findOldestExpense() {
        return expenseRepository.findOldestExpense()
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<ExpenseSummaryDTO> getExpenseSummaryForMonth(int year, int month) {
        return expenseRepository.getExpenseSummaryForMonth(year, month);
    }

    public Map<LocalDate, List<ExpenseSummaryDTO>> getExpenseSummariesForLastMonths(Integer lastMonths) {
        Map<LocalDate, List<ExpenseSummaryDTO>> expenseSummaries = new HashMap<>();
        LocalDate currentDate;

        if (lastMonths != null) {
            currentDate = LocalDate.now().minusMonths(lastMonths).withDayOfMonth(1);
        } else {
            currentDate = findOldestExpense().getTransactionDate().withDayOfMonth(1);
        }

        while (currentDate.isBefore(LocalDate.now().minusMonths(1))) {
            expenseSummaries.put(currentDate,
                    expenseRepository.getExpenseSummaryForMonth(currentDate.getYear(), currentDate.getMonthValue()));

            currentDate = currentDate.plusMonths(1);
        }

        return expenseSummaries;
    }

    public List<ExpenseCategory> getExpenseCategories() {
        return expenseCategoryRepository.findAllByOrderByName();
    }

    public void saveExpense(ExpenseDTO expense) {
        log.info("Saving expense for category {}", expense.getCategory());

        ExpenseCategory category = expenseCategoryRepository.findByName(expense.getCategory())
                .orElseThrow(() -> new RuntimeException("Expense category could not be found"));

        UserProfile user = userProfileRepository.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new UsernameNotFoundException("Username could not be found"));

        expenseRepository.saveAndFlush(new Expense(null, expense.getDate(), expense.getComment(), expense.getAmount(), category, user));

        log.info("Expense successfully saved");
    }

    public void deleteExpense(long id) {
        log.info("Removing expense with id {}", id);

        expenseRepository.deleteById(id);
    }

    public void updateExpenses(List<ExpenseDTO> expenses) {
        log.info("Updating expenses");

        UserProfile user = userProfileRepository.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new UsernameNotFoundException("Username could not be found"));

        List<Expense> expenseEntities = expenses.stream()
                .map(expense -> {
                    ExpenseCategory category = expenseCategoryRepository.findByName(expense.getCategory())
                            .orElseGet(() -> {
                                log.info("Creating new category for {}", expense.getCategory());

                                ExpenseCategory newCategory = new ExpenseCategory(null, expense.getCategory());

                                return expenseCategoryRepository.saveAndFlush(newCategory);
                            });
                    return new Expense(expense.getId(), expense.getDate(), expense.getComment(), expense.getAmount(), category, user);
                })
                .collect(Collectors.toList());

            expenseRepository.saveAll(expenseEntities);
    }

    public List<ExpenseDTO> getExpensesForMonth(int year, int month) {
        return expenseRepository.getExpensesForMonth(year, month);
    }
}

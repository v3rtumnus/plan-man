package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.expense.Expense;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ExpenseRepositoryTest {

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ExpenseRepository expenseRepository;

    private ExpenseCategory food;
    private ExpenseCategory travel;
    private UserProfile user;

    @BeforeEach
    void setUp() {
        user = new UserProfile();
        user.setUsername("testuser");
        user.setPassword("pw");
        em.persist(user);

        food = em.persist(new ExpenseCategory(null, "Food"));
        travel = em.persist(new ExpenseCategory(null, "Travel"));

        // Expenses in March 2024
        em.persist(new Expense(null, LocalDate.of(2024, 3, 5), "Lunch", new BigDecimal("30.00"), food, user));
        em.persist(new Expense(null, LocalDate.of(2024, 3, 20), "Dinner", new BigDecimal("50.00"), food, user));
        em.persist(new Expense(null, LocalDate.of(2024, 3, 15), "Flight", new BigDecimal("200.00"), travel, user));

        // Expense in January 2023 (oldest)
        em.persist(new Expense(null, LocalDate.of(2023, 1, 10), "Old expense", new BigDecimal("10.00"), food, user));

        em.flush();
    }

    // --- findOldestExpenseDate ---

    @Test
    void findOldestExpenseDate_returnsEarliestDate() {
        LocalDate oldest = expenseRepository.findOldestExpenseDate();
        assertThat(oldest).isEqualTo(LocalDate.of(2023, 1, 10));
    }

    // --- getExpenseSummaryForMonth ---

    @Test
    void getExpenseSummaryForMonth_aggregatesAmountsPerCategory() {
        List<ExpenseSummary> summaries = expenseRepository.getExpenseSummaryForMonth(2024, 3);

        assertThat(summaries).isNotEmpty();

        ExpenseSummary foodSummary = summaries.stream()
                .filter(s -> "Food".equals(s.getCategory()))
                .findFirst()
                .orElseThrow();
        assertThat(foodSummary.getAmount()).isEqualByComparingTo(new BigDecimal("80.00"));

        ExpenseSummary travelSummary = summaries.stream()
                .filter(s -> "Travel".equals(s.getCategory()))
                .findFirst()
                .orElseThrow();
        assertThat(travelSummary.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void getExpenseSummaryForMonth_monthWithNoExpenses_returnsEmptyList() {
        List<ExpenseSummary> summaries = expenseRepository.getExpenseSummaryForMonth(2024, 7);
        assertThat(summaries).isEmpty();
    }

    // --- getExpensesForMonth ---

    @Test
    void getExpensesForMonth_returnsExpenseDTOsForMonth() {
        List<ExpenseDTO> expenses = expenseRepository.getExpensesForMonth(2024, 3);

        assertThat(expenses).hasSize(3);
        // Verify DTOs have amount and category
        assertThat(expenses).allMatch(e -> e.getAmount() != null);
        assertThat(expenses).allMatch(e -> e.getCategory() != null);
    }

    @Test
    void getExpensesForMonth_orderedByDateDesc() {
        List<ExpenseDTO> expenses = expenseRepository.getExpensesForMonth(2024, 3);

        assertThat(expenses).hasSize(3);
        // Most recent first
        assertThat(expenses.get(0).getDate()).isAfterOrEqualTo(expenses.get(1).getDate());
        assertThat(expenses.get(1).getDate()).isAfterOrEqualTo(expenses.get(2).getDate());
    }

    @Test
    void getExpensesForMonth_noExpenses_returnsEmpty() {
        List<ExpenseDTO> expenses = expenseRepository.getExpensesForMonth(2022, 1);
        assertThat(expenses).isEmpty();
    }
}

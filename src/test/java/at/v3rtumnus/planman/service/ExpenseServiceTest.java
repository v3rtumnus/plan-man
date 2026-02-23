package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.ExpenseCategoryRepository;
import at.v3rtumnus.planman.dao.ExpenseRepository;
import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.expense.Expense;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private ExpenseService expenseService;

    private static final String TEST_USER = "testuser";

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContext ctx = new SecurityContextImpl();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(TEST_USER, "pw"));
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- getMinimumExpenseDate ---

    @Test
    void getMinimumExpenseDate_delegatesToRepository() {
        LocalDate expected = LocalDate.of(2022, 1, 1);
        when(expenseRepository.findOldestExpenseDate()).thenReturn(expected);

        assertThat(expenseService.getMinimumExpenseDate()).isEqualTo(expected);
        verify(expenseRepository).findOldestExpenseDate();
    }

    // --- getExpenseSummaryForMonth ---

    @Test
    void getExpenseSummaryForMonth_delegatesToRepository() {
        List<ExpenseSummary> expected = List.of(new ExpenseSummary("Food", BigDecimal.TEN));
        when(expenseRepository.getExpenseSummaryForMonth(2024, 3)).thenReturn(expected);

        List<ExpenseSummary> result = expenseService.getExpenseSummaryForMonth(2024, 3);

        assertThat(result).isEqualTo(expected);
    }

    // --- getExpenseSummariesForLastMonths ---

    @Test
    void getExpenseSummariesForLastMonths_aggregatesCorrectlyAndAddsGesamt() {
        when(expenseRepository.getExpenseSummaryForMonth(anyInt(), anyInt()))
                .thenReturn(new java.util.ArrayList<>(List.of(
                        new ExpenseSummary("Food", new BigDecimal("100")),
                        new ExpenseSummary("Travel", new BigDecimal("50"))
                )));

        Map<LocalDate, List<ExpenseSummary>> result = expenseService.getExpenseSummariesForLastMonths(2);

        // Each month's list should have a "Gesamt" entry appended
        result.values().forEach(summaries -> {
            assertThat(summaries).anyMatch(s -> ExpenseService.LABEL_OVERALL.equals(s.getCategory()));
            ExpenseSummary total = summaries.stream()
                    .filter(s -> ExpenseService.LABEL_OVERALL.equals(s.getCategory()))
                    .findFirst().orElseThrow();
            assertThat(total.getAmount()).isEqualByComparingTo(new BigDecimal("150"));
        });
    }

    @Test
    void getExpenseSummariesForLastMonths_nullParameter_usesMinimumDate() {
        LocalDate minDate = LocalDate.now().minusMonths(3);
        when(expenseRepository.findOldestExpenseDate()).thenReturn(minDate);
        when(expenseRepository.getExpenseSummaryForMonth(anyInt(), anyInt()))
                .thenReturn(new java.util.ArrayList<>());

        Map<LocalDate, List<ExpenseSummary>> result = expenseService.getExpenseSummariesForLastMonths(null);

        verify(expenseRepository).findOldestExpenseDate();
        assertThat(result).isNotNull();
    }

    // --- saveExpense ---

    @Test
    void saveExpense_linksExpenseToAuthenticatedUser() {
        ExpenseCategory cat = new ExpenseCategory(1L, "Food");
        UserProfile user = new UserProfile();
        user.setUsername(TEST_USER);
        Expense saved = new Expense(1L, LocalDate.now(), "Lunch", BigDecimal.TEN, cat, user);

        when(expenseCategoryRepository.findByName("Food")).thenReturn(Optional.of(cat));
        when(userProfileRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(user));
        when(expenseRepository.saveAndFlush(any(Expense.class))).thenReturn(saved);

        ExpenseDTO dto = new ExpenseDTO(null, BigDecimal.TEN, "Food", "Lunch", LocalDate.now());
        Expense result = expenseService.saveExpense(dto);

        assertThat(result.getUser().getUsername()).isEqualTo(TEST_USER);
        verify(expenseRepository).saveAndFlush(any(Expense.class));
    }

    // --- deleteExpense ---

    @Test
    void deleteExpense_delegatesToRepository() {
        expenseService.deleteExpense(42L);
        verify(expenseRepository).deleteById(42L);
    }

    // --- updateExpenses ---

    @Test
    void updateExpenses_usesExistingCategoryWhenPresent() {
        UserProfile user = new UserProfile();
        user.setUsername(TEST_USER);
        ExpenseCategory existing = new ExpenseCategory(1L, "Food");

        when(userProfileRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(user));
        when(expenseCategoryRepository.findByName("Food")).thenReturn(Optional.of(existing));

        ExpenseDTO dto = new ExpenseDTO(null, BigDecimal.TEN, "Food", "Lunch", LocalDate.now());
        expenseService.updateExpenses(List.of(dto));

        verify(expenseCategoryRepository, never()).saveAndFlush(any());
        verify(expenseRepository).saveAll(anyList());
    }

    @Test
    void updateExpenses_autoCreatesMissingCategory() {
        UserProfile user = new UserProfile();
        user.setUsername(TEST_USER);
        ExpenseCategory newCat = new ExpenseCategory(2L, "NewCat");

        when(userProfileRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(user));
        when(expenseCategoryRepository.findByName("NewCat")).thenReturn(Optional.empty());
        when(expenseCategoryRepository.saveAndFlush(any())).thenReturn(newCat);

        ExpenseDTO dto = new ExpenseDTO(null, BigDecimal.TEN, "NewCat", "Something", LocalDate.now());
        expenseService.updateExpenses(List.of(dto));

        verify(expenseCategoryRepository).saveAndFlush(any(ExpenseCategory.class));
    }
}

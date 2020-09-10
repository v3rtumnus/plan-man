package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.entity.OngoingExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OngoingExpensesRepository extends JpaRepository<OngoingExpense, Long> {

    @Query("SELECT MIN(e.transactionDate) FROM OngoingExpense e")
    LocalDate findOldestOngoingExpenseDate();

    @Query("SELECT new at.v3rtumnus.planman.dto.expense.ExpenseSummary(e.category.name, SUM(e.amount)) FROM OngoingExpense e " +
            "WHERE month(e.transactionDate) = :month AND year(e.transactionDate) = :year " +
            "GROUP BY e.category")
    List<ExpenseSummary> getExpenseSummaryForMonth(@Param("year") int year, @Param("month") int month);
}

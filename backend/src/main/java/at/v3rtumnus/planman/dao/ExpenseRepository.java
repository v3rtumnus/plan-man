package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseSummaryDTO;
import at.v3rtumnus.planman.entity.expense.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("SELECT e FROM Expense e WHERE e.transactionDate = (SELECT MIN(f.transactionDate) FROM Expense f)")
    List<Expense> findOldestExpense();

    @Query("SELECT new at.v3rtumnus.planman.dto.expense.ExpenseSummaryDTO(e.category.name, SUM(e.amount)) FROM Expense e " +
            "WHERE month(e.transactionDate) = :month AND year(e.transactionDate) = :year " +
            "GROUP BY e.category")
    List<ExpenseSummaryDTO> getExpenseSummaryForMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT new at.v3rtumnus.planman.dto.expense.ExpenseDTO(e.id, e.amount, e.category.name, e.comment, e.transactionDate) FROM Expense e " +
            "WHERE month(e.transactionDate) = :month AND year(e.transactionDate) = :year " +
            "ORDER BY e.transactionDate DESC")
    List<ExpenseDTO> getExpensesForMonth(@Param("year") int year, @Param("month") int month);
}

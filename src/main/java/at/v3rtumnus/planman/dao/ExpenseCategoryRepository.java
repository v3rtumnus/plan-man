package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findAllByOrderByName();

    Optional<ExpenseCategory> findByName(String name);
}

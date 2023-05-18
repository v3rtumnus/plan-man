package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.finance.FinancialProduct;
import at.v3rtumnus.planman.entity.finance.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    List<FinancialTransaction> findAllByOrderByTransactionDateDesc();
}

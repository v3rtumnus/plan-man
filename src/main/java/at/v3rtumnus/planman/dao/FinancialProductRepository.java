package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.credit.CreditSingleTransaction;
import at.v3rtumnus.planman.entity.finance.FinancialProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinancialProductRepository extends JpaRepository<FinancialProduct, Long> {
}

package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.finance.Dividend;
import at.v3rtumnus.planman.entity.finance.FinancialProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {
}

package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.finance.FinancialProduct;
import at.v3rtumnus.planman.entity.finance.FinancialProductStockQuote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialProductStockQuoteRepository extends JpaRepository<FinancialProductStockQuote, Long> {

    @Query("SELECT q FROM FinancialProductStockQuote q WHERE q.financialProduct.isin = :isin")
    Page<FinancialProductStockQuote> findByProduct(@Param("isin") String isin, Pageable pageable);
}

package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.CreditSingleTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditSinglePaymentRepository extends JpaRepository<CreditSingleTransaction, Long> {

    List<CreditSingleTransaction> findAllByOrderByTransactionDate();
}

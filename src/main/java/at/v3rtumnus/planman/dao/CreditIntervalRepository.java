package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.credit.CreditInterval;
import at.v3rtumnus.planman.entity.credit.CreditSingleTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditIntervalRepository extends JpaRepository<CreditInterval, Long> {

    @Query("SELECT c FROM CreditInterval c ORDER BY c.validUntilDate NULLS LAST")
    List<CreditInterval> findAllOrderedCreditIntervals();
}

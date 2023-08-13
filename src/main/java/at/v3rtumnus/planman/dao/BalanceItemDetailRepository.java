package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.balance.BalanceGroup;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.balance.BalanceItemDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BalanceItemDetailRepository extends JpaRepository<BalanceItemDetail, Long> {

    @Query("SELECT MIN(begin) FROM BalanceItemDetail")
    LocalDate getMinimumBalanceDate();

    @Query("SELECT b FROM BalanceItemDetail b WHERE (:date BETWEEN b.begin AND b.end) OR (:date >= b.begin AND b.end = NULL)")
    List<BalanceItemDetail> getRelevantAmountsForDate(@Param("date") LocalDate date);
}

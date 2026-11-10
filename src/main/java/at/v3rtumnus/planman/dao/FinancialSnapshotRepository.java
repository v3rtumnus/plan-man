package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.finance.FinancialSnapshot;
import at.v3rtumnus.planman.entity.finance.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialSnapshotRepository extends JpaRepository<FinancialSnapshot, Long> {

    List<FinancialSnapshot> findAllByOrderBySnapshotDate();

    Optional<FinancialSnapshot> findFirstBySnapshotDateLessThanEqualOrderBySnapshotDateDesc(LocalDate date);
}

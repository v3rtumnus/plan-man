package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.finance.SavingsPlan;
import at.v3rtumnus.planman.entity.finance.UploadLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsPlanRepository extends JpaRepository<SavingsPlan, Long> {

    List<SavingsPlan> findByEndDateIsNull();
}

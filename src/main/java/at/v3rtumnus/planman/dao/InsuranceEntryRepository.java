package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.insurance.InsuranceEntry;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface InsuranceEntryRepository extends JpaRepository<InsuranceEntry, Long>, FilterableInsuranceEntryRepository {

    @Query("SELECT MIN(i.entryDate) FROM InsuranceEntry i")
    Optional<LocalDate> getOldestInsuranceEntryDate();

    @Modifying
    @Query("UPDATE InsuranceEntry i SET i.state = :state WHERE i.id = :id")
    void updateState(Long id, InsuranceEntryState state);

    @Modifying
    @Query("UPDATE InsuranceEntry i SET i.state = :state, i.healthInsuranceAmount = :amount, " +
            "i.healthInsuranceFilename = :fileName, i.healthInsuranceData = :fileData WHERE i.id = :id")
    void updateStateWithHealthInsurance(Long id, InsuranceEntryState state, BigDecimal amount, String fileName, byte[] fileData);

    @Modifying
    @Query("UPDATE InsuranceEntry i SET i.state = :state, i.privateInsuranceAmount = :amount, " +
            "i.privateInsuranceFilename = :fileName, i.privateInsuranceData = :fileData WHERE i.id = :id")
    void updateStateWithPrivateInsurance(Long id, InsuranceEntryState state, BigDecimal amount, String fileName, byte[] fileData);

    @Modifying
    @Query("UPDATE InsuranceEntry i SET i.healthInsuranceAmountReceived = TRUE WHERE i.id = :id")
    void updateHealthAmountReceived(Long id);

    @Modifying
    @Query("UPDATE InsuranceEntry i SET i.privateInsuranceAmountReceived = TRUE WHERE i.id = :id")
    void updatePrivateAmountReceived(Long id);
}

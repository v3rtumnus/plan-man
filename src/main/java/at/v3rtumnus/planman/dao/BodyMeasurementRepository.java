package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.health.BodyMeasurement;
import at.v3rtumnus.planman.entity.health.MeasurementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, Long> {

    Optional<BodyMeasurement> findByUserUsernameAndTypeAndMeasuredOn(String username, MeasurementType type,
                                                                     LocalDate measuredOn);

    Optional<BodyMeasurement> findFirstByUserUsernameAndTypeOrderByMeasuredOnDesc(String username,
                                                                                  MeasurementType type);

    List<BodyMeasurement> findByUserUsernameAndTypeAndMeasuredOnBetweenOrderByMeasuredOnAsc(
            String username, MeasurementType type, LocalDate from, LocalDate to);

    List<BodyMeasurement> findByUserUsernameAndTypeOrderByMeasuredOnAsc(String username, MeasurementType type);
}

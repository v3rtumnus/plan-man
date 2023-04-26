package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.finance.UploadLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UploadLogRepository extends JpaRepository<UploadLog, Long> {

    Optional<UploadLog> findByFilename(String filename);
}

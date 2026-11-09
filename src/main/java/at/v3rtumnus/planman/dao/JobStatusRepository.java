package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStatusRepository extends JpaRepository<JobStatus, String> {
}

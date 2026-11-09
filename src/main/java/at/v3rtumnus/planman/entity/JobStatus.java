package at.v3rtumnus.planman.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Last success and failure of a scheduled job; feeds the deadman metric. */
@Entity
@Data
@NoArgsConstructor
public class JobStatus {

    @Id
    private String name;

    private LocalDateTime lastSuccessAt;

    private LocalDateTime lastFailureAt;

    private String lastError;
}

package at.v3rtumnus.planman.entity.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "FINANCIAL_UPLOAD_LOG")
public class UploadLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;
    private LocalDate importedAt;

    public UploadLog(String filename, LocalDate importedAt) {
        this.filename = filename;
        this.importedAt = importedAt;
    }
}

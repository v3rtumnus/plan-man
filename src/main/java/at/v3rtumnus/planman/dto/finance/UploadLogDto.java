package at.v3rtumnus.planman.dto.finance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class UploadLogDto {
    private String filename;

    @JsonFormat(pattern="dd.MM.yyyy")
    private LocalDate date;
}

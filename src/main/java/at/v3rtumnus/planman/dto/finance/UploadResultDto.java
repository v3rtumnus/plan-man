package at.v3rtumnus.planman.dto.finance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder(access = AccessLevel.PUBLIC)
public class UploadResultDto {
    private UploadResult result;
    private String filename;

    @JsonFormat(pattern="dd.MM.yyyy")
    private LocalDate date;
    private String error;

    private String isin;
    private BigDecimal quantity;
    private BigDecimal amount;
    private UploadType type;
}

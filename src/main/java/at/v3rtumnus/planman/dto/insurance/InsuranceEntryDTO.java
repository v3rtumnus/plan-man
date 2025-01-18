package at.v3rtumnus.planman.dto.insurance;

import at.v3rtumnus.planman.entity.insurance.InsuranceEntry;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryType;
import at.v3rtumnus.planman.entity.insurance.InsuranceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceEntryDTO {
    private Long id;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;
    private String person;
    private InsuranceEntryType type;
    private InsuranceType insuranceType;
    private String doctor;
    private InsuranceEntryState state;
    private BigDecimal amount;
    private String invoiceFilename;
    private byte[] invoiceData;
    private String healthInsuranceFilename;
    private byte[] healthInsuranceData;
    private BigDecimal healthInsuranceAmount;
    private boolean healthInsuranceAmountReceived;
    private String privateInsuranceFilename;
    private byte[] privateInsuranceData;
    private BigDecimal privateInsuranceAmount;
    private boolean privateInsuranceAmountReceived;
    private BigDecimal retention;

    public InsuranceEntryDTO(LocalDate entryDate, String person, InsuranceEntryType type, InsuranceType insuranceType, String doctor, InsuranceEntryState state, BigDecimal amount, String invoiceFilename, byte[] invoiceData) {
        this.entryDate = entryDate;
        this.person = person;
        this.type = type;
        this.insuranceType = insuranceType;
        this.doctor = doctor;
        this.state = state;
        this.amount = amount;
        this.invoiceFilename = invoiceFilename;
        this.invoiceData = invoiceData;
    }

    public static InsuranceEntryDTO fromInsuranceEntry(InsuranceEntry entry) {
        BigDecimal insuranceAmount = Optional.ofNullable(entry.getHealthInsuranceAmount()).orElse(BigDecimal.ZERO)
                .add(Optional.ofNullable(entry.getPrivateInsuranceAmount()).orElse(BigDecimal.ZERO));

        BigDecimal retention = entry.getState() == InsuranceEntryState.DONE ?
                entry.getAmount().subtract(insuranceAmount) : null;


        return new InsuranceEntryDTO(entry.getId(), entry.getEntryDate(), entry.getPerson().getName(),
                entry.getType(), entry.getInsuranceType(), entry.getName(), entry.getState(), entry.getAmount(), entry.getInvoiceFilename(), entry.getInvoiceData(),
                entry.getHealthInsuranceFilename(), entry.getHealthInsuranceData(), entry.getHealthInsuranceAmount(), entry.isHealthInsuranceAmountReceived(), entry.getPrivateInsuranceFilename(),
                entry.getPrivateInsuranceData(), entry.getPrivateInsuranceAmount(), entry.isPrivateInsuranceAmountReceived(), retention);
    }

    public InsuranceEntryState getCalculatedState() {
        return insuranceType == InsuranceType.PRIVATE && state == InsuranceEntryState.HEALH_INSURANCE_RECEIVED ? InsuranceEntryState.RECORDED : state;
    }

    public boolean isHealthAmountOpen() {
        return insuranceType == InsuranceType.HEALTH && !healthInsuranceAmountReceived &&
                (state == InsuranceEntryState.HEALH_INSURANCE_RECEIVED ||
                        state == InsuranceEntryState.WAITING_FOR_PRIVATE_INSURANCE ||
                        state == InsuranceEntryState.DONE);
    }

    public boolean isPrivateAmountOpen() {
        return state == InsuranceEntryState.DONE && !privateInsuranceAmountReceived;
    }
}

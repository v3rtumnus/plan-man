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

    /**
     * List-view constructor used by the JPQL projection in FilterableInsuranceEntryRepositoryImpl.
     * Leaves out the attachment BLOBs so listing entries doesn't load every file into memory.
     */
    public InsuranceEntryDTO(Long id, LocalDate entryDate, String person, InsuranceEntryType type, InsuranceType insuranceType,
                             String doctor, InsuranceEntryState state, BigDecimal amount, String invoiceFilename,
                             String healthInsuranceFilename, BigDecimal healthInsuranceAmount, boolean healthInsuranceAmountReceived,
                             String privateInsuranceFilename, BigDecimal privateInsuranceAmount, boolean privateInsuranceAmountReceived) {
        this(id, entryDate, person, type, insuranceType, doctor, state, amount, invoiceFilename, null,
                healthInsuranceFilename, null, healthInsuranceAmount, healthInsuranceAmountReceived,
                privateInsuranceFilename, null, privateInsuranceAmount, privateInsuranceAmountReceived,
                calculateRetention(state, amount, healthInsuranceAmount, privateInsuranceAmount));
    }

    private static BigDecimal calculateRetention(InsuranceEntryState state, BigDecimal amount,
                                                 BigDecimal healthInsuranceAmount, BigDecimal privateInsuranceAmount) {
        BigDecimal insuranceAmount = Optional.ofNullable(healthInsuranceAmount).orElse(BigDecimal.ZERO)
                .add(Optional.ofNullable(privateInsuranceAmount).orElse(BigDecimal.ZERO));

        return state == InsuranceEntryState.DONE ? amount.subtract(insuranceAmount) : null;
    }

    public static InsuranceEntryDTO fromInsuranceEntry(InsuranceEntry entry) {
        BigDecimal retention = calculateRetention(entry.getState(), entry.getAmount(),
                entry.getHealthInsuranceAmount(), entry.getPrivateInsuranceAmount());


        return new InsuranceEntryDTO(entry.getId(), entry.getEntryDate(), entry.getPerson().getName(),
                entry.getType(), entry.getInsuranceType(), entry.getName(), entry.getState(), entry.getAmount(), entry.getInvoiceFilename(), null,
                entry.getHealthInsuranceFilename(), null, entry.getHealthInsuranceAmount(), entry.isHealthInsuranceAmountReceived(), entry.getPrivateInsuranceFilename(),
                null, entry.getPrivateInsuranceAmount(), entry.isPrivateInsuranceAmountReceived(), retention);
    }

    public InsuranceEntryState getCalculatedState() {
        return insuranceType == InsuranceType.PRIVATE && state == InsuranceEntryState.HEALH_INSURANCE_RECEIVED ? InsuranceEntryState.RECORDED : state;
    }

    public boolean isHealthAmountOpen() {
        return healthInsuranceAmount != null && healthInsuranceAmount.compareTo(BigDecimal.ZERO) > 0 && !healthInsuranceAmountReceived;
    }

    public boolean isHealthAmountReceived() {
        return healthInsuranceAmount != null && healthInsuranceAmount.compareTo(BigDecimal.ZERO) > 0 && healthInsuranceAmountReceived;
    }

    public boolean isPrivateAmountOpen() {
        return privateInsuranceAmount != null && privateInsuranceAmount.compareTo(BigDecimal.ZERO) > 0 && !privateInsuranceAmountReceived;
    }

    public boolean isPrivateAmountReceived() {
        return privateInsuranceAmount != null && privateInsuranceAmount.compareTo(BigDecimal.ZERO) > 0 && privateInsuranceAmountReceived;
    }
}

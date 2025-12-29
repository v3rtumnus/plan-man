package at.v3rtumnus.planman.entity.insurance;

import at.v3rtumnus.planman.entity.expense.Expense;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "INSURANCE_ENTRY")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    @NotNull
    private LocalDate entryDate;

    @ManyToOne
    @JoinColumn(name = "INSURANCE_PERSON_ID")
    @NotNull
    private InsurancePerson person;

    @Enumerated(EnumType.STRING)
    @NotNull
    private InsuranceEntryType type;

    @Enumerated(EnumType.STRING)
    @NotNull
    private InsuranceType insuranceType;

    @Column
    private String name;

    @Enumerated(EnumType.STRING)
    @NotNull
    private InsuranceEntryState state;

    @Column
    @NotNull
    private BigDecimal amount;

    @Column
    private String invoiceFilename;

    @Lob
    @Column
    private byte[] invoiceData;

    @Column
    private String healthInsuranceFilename;

    @Lob
    @Column
    private byte[] healthInsuranceData;

    @Column
    private BigDecimal healthInsuranceAmount;

    @Column
    private boolean healthInsuranceAmountReceived;

    @Column
    private String privateInsuranceFilename;

    @Column
    private boolean privateInsuranceAmountReceived;

    @Lob
    @Column
    private byte[] privateInsuranceData;

    @Column
    private BigDecimal privateInsuranceAmount;

    @ManyToOne
    @JoinColumn(name = "EXPENSE_ID")
    private Expense expense;

    public InsuranceEntry(LocalDate entryDate, InsurancePerson person, InsuranceEntryType type, InsuranceType insuranceType, String doctor,
                          InsuranceEntryState state, BigDecimal amount, String invoiceFilename, byte[] invoiceData, Expense expense) {
        this.entryDate = entryDate;
        this.person = person;
        this.type = type;
        this.insuranceType = insuranceType;
        this.name = doctor;
        this.state = state;
        this.amount = amount;
        this.invoiceFilename = invoiceFilename;
        this.invoiceData = invoiceData;
        this.expense = expense;
    }
}

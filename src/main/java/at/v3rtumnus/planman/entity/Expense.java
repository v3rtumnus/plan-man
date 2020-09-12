package at.v3rtumnus.planman.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "EXPENSE")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private LocalDate transactionDate;

    @Column
    private String comment;

    @Column
    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "EXPENSE_CATEGORY_ID")
    private ExpenseCategory category;

    @ManyToOne
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;
}

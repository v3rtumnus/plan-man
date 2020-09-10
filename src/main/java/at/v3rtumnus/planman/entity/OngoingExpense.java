package at.v3rtumnus.planman.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "EXPENSE_ONGOING")
@Data
public class OngoingExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column
    private LocalDate transactionDate;

    @Column
    private String comment;

    @Column
    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "EXPENSE_CATEGORY_ID")
    private ExpenseGategory category;

    @ManyToOne
    @JoinColumn(name = "USER_PROFILE_ID")
    private UserProfile user;
}

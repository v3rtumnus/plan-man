package at.v3rtumnus.planman.entity.balance;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "monthly_balance_item_details")
@Getter
@Setter
@NoArgsConstructor
public class BalanceItemDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal amount;
    private LocalDate begin;
    private LocalDate end;

    @ManyToOne
    @JoinColumn(name = "balance_item_id")
    private BalanceItem item;

    public BalanceItemDetail(BigDecimal amount, LocalDate begin, LocalDate end, BalanceItem item) {
        this.amount = amount;
        this.begin = begin;
        this.end = end;
        this.item = item;
    }
}
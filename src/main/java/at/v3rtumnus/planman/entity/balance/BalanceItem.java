package at.v3rtumnus.planman.entity.balance;

import at.v3rtumnus.planman.entity.finance.FinancialProduct;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "monthly_balance_item")
@Getter
@Setter
@NoArgsConstructor
public class BalanceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "balance_group_id")
    private BalanceGroup group;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL)
    private List<BalanceItemDetail> details;

    public BalanceItem(String name, BalanceGroup group) {
        this.name = name;
        this.group = group;
    }
}
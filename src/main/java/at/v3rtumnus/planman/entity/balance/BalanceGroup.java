package at.v3rtumnus.planman.entity.balance;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "monthly_balance_group")
@Getter
@Setter
@NoArgsConstructor
public class BalanceGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Enumerated(value = EnumType.STRING)
    private BalanceGroupType type;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    private List<BalanceItem> items;

    public BalanceGroup(String name, BalanceGroupType type) {
        this.name = name;
        this.type = type;
    }
}
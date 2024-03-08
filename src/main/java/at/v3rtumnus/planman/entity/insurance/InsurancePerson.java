package at.v3rtumnus.planman.entity.insurance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "INSURANCE_PERSON")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsurancePerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;
}

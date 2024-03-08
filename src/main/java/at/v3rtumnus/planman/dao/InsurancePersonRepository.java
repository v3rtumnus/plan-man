package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.insurance.InsuranceEntry;
import at.v3rtumnus.planman.entity.insurance.InsurancePerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InsurancePersonRepository extends JpaRepository<InsurancePerson, Long> {

    public Optional<InsurancePerson> findByName(String name);
}

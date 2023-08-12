package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.balance.BalanceGroup;
import at.v3rtumnus.planman.entity.balance.BalanceGroupType;
import at.v3rtumnus.planman.entity.credit.CreditInterval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BalanceGroupRepository extends JpaRepository<BalanceGroup, Long> {

    @Query("SELECT b FROM BalanceGroup b WHERE b.type = :type AND b.name = :name")
    public Optional<BalanceGroup> find(@Param("type") BalanceGroupType type, @Param("name") String name);
}

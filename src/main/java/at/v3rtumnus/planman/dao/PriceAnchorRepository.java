package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.finance.PriceAnchor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceAnchorRepository extends JpaRepository<PriceAnchor, String> {
}

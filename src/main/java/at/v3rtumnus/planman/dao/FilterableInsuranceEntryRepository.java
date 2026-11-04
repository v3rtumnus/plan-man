package at.v3rtumnus.planman.dao;


import at.v3rtumnus.planman.dto.insurance.InsuranceEntryDTO;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;

import java.util.List;

public interface FilterableInsuranceEntryRepository {

    List<InsuranceEntryDTO> getFilteredInsuranceEntries(String year, String person, InsuranceEntryState state);
}

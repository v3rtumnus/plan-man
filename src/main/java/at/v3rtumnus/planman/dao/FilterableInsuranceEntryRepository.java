package at.v3rtumnus.planman.dao;


import at.v3rtumnus.planman.entity.insurance.InsuranceEntry;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;

import java.util.List;

public interface FilterableInsuranceEntryRepository {

    List<InsuranceEntry> getFilteredInsuranceEntries(String year, String person, InsuranceEntryState state);
}

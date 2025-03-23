package at.v3rtumnus.planman.dao;


import at.v3rtumnus.planman.entity.insurance.InsuranceEntry;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.List;

public class FilterableInsuranceEntryRepositoryImpl implements FilterableInsuranceEntryRepository{

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<InsuranceEntry> getFilteredInsuranceEntries(String year, String person, InsuranceEntryState state) {
        String queryString = "FROM InsuranceEntry e ";

        boolean whereExists = false;

        if (year != null) {
            queryString += "WHERE YEAR(e.entryDate) = :year ";
            whereExists = true;
        }
        if (person != null) {
            String clause = "e.person.name = :person ";
            queryString += !whereExists ? "WHERE " + clause : "AND " + clause;

            whereExists = true;
        }

        if (state != null) {
            String clause = "e.state = :state";
            queryString += !whereExists ? "WHERE " + clause : "AND " + clause;
        }

        queryString += " ORDER BY e.entryDate DESC";

        Query query = entityManager.createQuery(queryString);

        if (year != null) {
            query.setParameter("year", year);
        }
        if (person != null) {
            query.setParameter("person", person);
        }

        if (state != null) {
            query.setParameter("state", state);
        }
        
        return query.getResultList();
    }
}

package at.v3rtumnus.planman.dao;


import at.v3rtumnus.planman.dto.insurance.InsuranceEntryDTO;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class FilterableInsuranceEntryRepositoryImpl implements FilterableInsuranceEntryRepository{

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<InsuranceEntryDTO> getFilteredInsuranceEntries(String year, String person, InsuranceEntryState state) {
        // Select only the list columns: loading the entity would pull every attachment BLOB into memory
        // (@Basic(fetch = LAZY) is ignored without Hibernate bytecode enhancement).
        String queryString = "SELECT new at.v3rtumnus.planman.dto.insurance.InsuranceEntryDTO(" +
                "e.id, e.entryDate, e.person.name, e.type, e.insuranceType, e.name, e.state, e.amount, e.invoiceFilename, " +
                "e.healthInsuranceFilename, e.healthInsuranceAmount, e.healthInsuranceAmountReceived, " +
                "e.privateInsuranceFilename, e.privateInsuranceAmount, e.privateInsuranceAmountReceived) " +
                "FROM InsuranceEntry e ";

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

        TypedQuery<InsuranceEntryDTO> query = entityManager.createQuery(queryString, InsuranceEntryDTO.class);

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

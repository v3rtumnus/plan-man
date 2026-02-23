package at.v3rtumnus.planman.dao;

import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.expense.Expense;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
import at.v3rtumnus.planman.entity.insurance.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class InsuranceEntryRepositoryTest {

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private InsuranceEntryRepository insuranceEntryRepository;

    private InsurancePerson alice;
    private InsurancePerson bob;
    private ExpenseCategory arzt;
    private UserProfile user;

    @BeforeEach
    void setUp() {
        user = new UserProfile();
        user.setUsername("testuser");
        user.setPassword("pw");
        em.persist(user);

        alice = em.persist(new InsurancePerson(null, "Alice"));
        bob = em.persist(new InsurancePerson(null, "Bob"));
        arzt = em.persist(new ExpenseCategory(null, "Arzt"));

        em.flush();
    }

    private InsuranceEntry entry(InsurancePerson person, LocalDate date, InsuranceEntryState state) {
        Expense expense = new Expense(null, date, "Doc", new BigDecimal("100"),
                arzt, user);
        em.persist(expense);

        return new InsuranceEntry(date, person, InsuranceEntryType.DOCTOR,
                InsuranceType.HEALTH, "Dr. Smith", state,
                new BigDecimal("100"), null, null, expense);
    }

    // --- getOldestInsuranceEntryDate ---

    @Test
    void getOldestInsuranceEntryDate_returnsEarliestDate() {
        em.persist(entry(alice, LocalDate.of(2023, 6, 1), InsuranceEntryState.DONE));
        em.persist(entry(alice, LocalDate.of(2024, 1, 15), InsuranceEntryState.RECORDED));
        em.flush();

        Optional<LocalDate> oldest = insuranceEntryRepository.getOldestInsuranceEntryDate();
        assertThat(oldest).isPresent();
        assertThat(oldest.get()).isEqualTo(LocalDate.of(2023, 6, 1));
    }

    @Test
    void getOldestInsuranceEntryDate_noEntries_returnsEmpty() {
        Optional<LocalDate> oldest = insuranceEntryRepository.getOldestInsuranceEntryDate();
        assertThat(oldest).isEmpty();
    }

    // --- getFilteredInsuranceEntries ---

    @Test
    void getFilteredInsuranceEntries_filterByPerson_returnsOnlyThatPerson() {
        em.persist(entry(alice, LocalDate.of(2024, 3, 1), InsuranceEntryState.RECORDED));
        em.persist(entry(bob, LocalDate.of(2024, 3, 5), InsuranceEntryState.RECORDED));
        em.flush();

        List<InsuranceEntry> results = insuranceEntryRepository.getFilteredInsuranceEntries(
                null, "Alice", null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPerson().getName()).isEqualTo("Alice");
    }

    @Test
    void getFilteredInsuranceEntries_filterByState_returnsOnlyMatchingState() {
        em.persist(entry(alice, LocalDate.of(2024, 3, 1), InsuranceEntryState.RECORDED));
        em.persist(entry(alice, LocalDate.of(2024, 3, 5), InsuranceEntryState.DONE));
        em.flush();

        List<InsuranceEntry> results = insuranceEntryRepository.getFilteredInsuranceEntries(
                null, null, InsuranceEntryState.RECORDED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getState()).isEqualTo(InsuranceEntryState.RECORDED);
    }

    @Test
    void getFilteredInsuranceEntries_noFilters_returnsAll() {
        em.persist(entry(alice, LocalDate.of(2024, 3, 1), InsuranceEntryState.RECORDED));
        em.persist(entry(bob, LocalDate.of(2024, 4, 1), InsuranceEntryState.DONE));
        em.flush();

        List<InsuranceEntry> results = insuranceEntryRepository.getFilteredInsuranceEntries(
                null, null, null);

        assertThat(results).hasSize(2);
    }

    @Test
    void getFilteredInsuranceEntries_filterByPersonAndState_appliesBothFilters() {
        em.persist(entry(alice, LocalDate.of(2024, 3, 1), InsuranceEntryState.RECORDED));
        em.persist(entry(alice, LocalDate.of(2024, 4, 1), InsuranceEntryState.DONE));
        em.persist(entry(bob, LocalDate.of(2024, 5, 1), InsuranceEntryState.RECORDED));
        em.flush();

        List<InsuranceEntry> results = insuranceEntryRepository.getFilteredInsuranceEntries(
                null, "Alice", InsuranceEntryState.RECORDED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPerson().getName()).isEqualTo("Alice");
        assertThat(results.get(0).getState()).isEqualTo(InsuranceEntryState.RECORDED);
    }

    @Test
    void getFilteredInsuranceEntries_resultOrderedByDateDesc() {
        em.persist(entry(alice, LocalDate.of(2024, 1, 1), InsuranceEntryState.RECORDED));
        em.persist(entry(alice, LocalDate.of(2024, 6, 1), InsuranceEntryState.RECORDED));
        em.persist(entry(alice, LocalDate.of(2024, 3, 1), InsuranceEntryState.RECORDED));
        em.flush();

        List<InsuranceEntry> results = insuranceEntryRepository.getFilteredInsuranceEntries(
                null, null, null);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getEntryDate()).isAfterOrEqualTo(results.get(1).getEntryDate());
        assertThat(results.get(1).getEntryDate()).isAfterOrEqualTo(results.get(2).getEntryDate());
    }
}

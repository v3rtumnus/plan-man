package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.ExpenseRepository;
import at.v3rtumnus.planman.dao.InsuranceEntryRepository;
import at.v3rtumnus.planman.dao.InsurancePersonRepository;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.insurance.InsuranceEntryDTO;
import at.v3rtumnus.planman.entity.expense.Expense;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
import at.v3rtumnus.planman.entity.insurance.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuranceServiceTest {

    @Mock
    private InsuranceEntryRepository insuranceEntryRepository;

    @Mock
    private InsurancePersonRepository insurancePersonRepository;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private InsuranceService insuranceService;

    private InsuranceEntryDTO doctorDto() {
        InsuranceEntryDTO dto = new InsuranceEntryDTO();
        dto.setType(InsuranceEntryType.DOCTOR);
        dto.setInsuranceType(InsuranceType.HEALTH);
        dto.setAmount(new BigDecimal("120.00"));
        dto.setDoctor("Dr. Smith");
        dto.setEntryDate(LocalDate.now());
        dto.setState(InsuranceEntryState.RECORDED);
        dto.setPerson("Alice");
        return dto;
    }

    private InsuranceEntryDTO pharmacyDto() {
        InsuranceEntryDTO dto = new InsuranceEntryDTO();
        dto.setType(InsuranceEntryType.PHARMACY);
        dto.setInsuranceType(InsuranceType.HEALTH);
        dto.setAmount(new BigDecimal("25.00"));
        dto.setDoctor("Pharmacy XY");
        dto.setEntryDate(LocalDate.now());
        dto.setState(InsuranceEntryState.RECORDED);
        dto.setPerson("Bob");
        return dto;
    }

    // --- saveInsuranceEntry ---

    @Test
    void saveInsuranceEntry_doctorType_usesArztCategory() {
        InsurancePerson person = new InsurancePerson();
        person.setName("Alice");
        Expense dummyExpense = new Expense(1L, LocalDate.now(), "Dr. Smith",
                new BigDecimal("120.00"), new ExpenseCategory(1L, "Arzt"), null);

        when(insurancePersonRepository.findByName("Alice")).thenReturn(Optional.of(person));
        when(expenseService.saveExpense(any(ExpenseDTO.class))).thenReturn(dummyExpense);

        insuranceService.saveInsuranceEntry(doctorDto());

        ArgumentCaptor<ExpenseDTO> captor = ArgumentCaptor.forClass(ExpenseDTO.class);
        verify(expenseService).saveExpense(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("Arzt");
    }

    @Test
    void saveInsuranceEntry_pharmacyType_usesApothekeCategory() {
        InsurancePerson person = new InsurancePerson();
        person.setName("Bob");
        Expense dummyExpense = new Expense(2L, LocalDate.now(), "Pharmacy XY",
                new BigDecimal("25.00"), new ExpenseCategory(2L, "Apotheke"), null);

        when(insurancePersonRepository.findByName("Bob")).thenReturn(Optional.of(person));
        when(expenseService.saveExpense(any(ExpenseDTO.class))).thenReturn(dummyExpense);

        insuranceService.saveInsuranceEntry(pharmacyDto());

        ArgumentCaptor<ExpenseDTO> captor = ArgumentCaptor.forClass(ExpenseDTO.class);
        verify(expenseService).saveExpense(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("Apotheke");
    }

    @Test
    void saveInsuranceEntry_persistsEntry() {
        InsurancePerson person = new InsurancePerson();
        person.setName("Alice");
        Expense dummyExpense = new Expense(1L, LocalDate.now(), "Dr. Smith",
                new BigDecimal("120.00"), new ExpenseCategory(1L, "Arzt"), null);

        when(insurancePersonRepository.findByName("Alice")).thenReturn(Optional.of(person));
        when(expenseService.saveExpense(any())).thenReturn(dummyExpense);

        insuranceService.saveInsuranceEntry(doctorDto());

        verify(insuranceEntryRepository).saveAndFlush(any(InsuranceEntry.class));
    }

    // --- updateState (simple, no amount) ---

    @Test
    void updateState_simpleTransition_delegatesToRepository() {
        insuranceService.updateState(1L, InsuranceEntryState.WAITING_FOR_HEALTH_INSURANCE);
        verify(insuranceEntryRepository).updateState(1L, InsuranceEntryState.WAITING_FOR_HEALTH_INSURANCE);
    }

    // --- updateState (with amount — health insurance) ---

    @Test
    void updateState_healthInsurance_callsUpdateWithHealthInsurance() {
        InsuranceEntry entry = new InsuranceEntry();
        entry.setExpense(new Expense(1L, LocalDate.now(), "comment",
                new BigDecimal("100"), new ExpenseCategory(1L, "Arzt"), null));

        when(insuranceEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        insuranceService.updateState(1L, InsuranceEntryState.HEALH_INSURANCE_RECEIVED,
                new BigDecimal("80"), "file.pdf", new byte[0]);

        verify(insuranceEntryRepository).updateStateWithHealthInsurance(
                eq(1L), eq(InsuranceEntryState.HEALH_INSURANCE_RECEIVED),
                eq(new BigDecimal("80")), eq("file.pdf"), any());
    }

    @Test
    void updateState_healthInsurance_updatesExpenseAmount() {
        Expense expense = new Expense(1L, LocalDate.now(), "comment",
                new BigDecimal("100"), new ExpenseCategory(1L, "Arzt"), null);
        InsuranceEntry entry = new InsuranceEntry();
        entry.setExpense(expense);

        when(insuranceEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        insuranceService.updateState(1L, InsuranceEntryState.HEALH_INSURANCE_RECEIVED,
                new BigDecimal("80"), "file.pdf", new byte[0]);

        // Expense amount should be reduced by 80: 100 - 80 = 20
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("20"));
        verify(expenseRepository).save(expense);
    }

    // --- updateState (with amount — private insurance) ---

    @Test
    void updateState_privateInsurance_callsUpdateWithPrivateInsurance() {
        InsuranceEntry entry = new InsuranceEntry();
        entry.setExpense(new Expense(1L, LocalDate.now(), "comment",
                new BigDecimal("20"), new ExpenseCategory(1L, "Arzt"), null));

        when(insuranceEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        insuranceService.updateState(1L, InsuranceEntryState.DONE,
                new BigDecimal("15"), "priv.pdf", new byte[0]);

        verify(insuranceEntryRepository).updateStateWithPrivateInsurance(
                eq(1L), eq(InsuranceEntryState.DONE),
                eq(new BigDecimal("15")), eq("priv.pdf"), any());
    }

    // --- updateAmountReceived ---

    @Test
    void updateAmountReceived_healthType_callsHealthAmountReceived() {
        insuranceService.updateAmountReceived(1L, InsuranceType.HEALTH);
        verify(insuranceEntryRepository).updateHealthAmountReceived(1L);
    }

    @Test
    void updateAmountReceived_privateType_callsPrivateAmountReceived() {
        insuranceService.updateAmountReceived(1L, InsuranceType.PRIVATE);
        verify(insuranceEntryRepository).updatePrivateAmountReceived(1L);
    }

    // --- getInsuranceEntries ---

    @Test
    void getInsuranceEntries_delegatesFiltersToRepository() {
        when(insuranceEntryRepository.getFilteredInsuranceEntries("2024", "Alice", InsuranceEntryState.RECORDED))
                .thenReturn(java.util.Collections.emptyList());

        insuranceService.getInsuranceEntries("2024", "Alice", InsuranceEntryState.RECORDED);

        verify(insuranceEntryRepository).getFilteredInsuranceEntries("2024", "Alice", InsuranceEntryState.RECORDED);
    }

    // --- getPersons ---

    @Test
    void getPersons_returnsSortedPersonNames() {
        InsurancePerson bob = new InsurancePerson();
        bob.setName("Bob");
        InsurancePerson alice = new InsurancePerson();
        alice.setName("Alice");

        when(insurancePersonRepository.findAll()).thenReturn(java.util.List.of(bob, alice));

        java.util.List<String> persons = insuranceService.getPersons();

        assertThat(persons).containsExactly("Alice", "Bob");
    }

    // --- getYears ---

    @Test
    void getYears_returnsCurrentYearWhenNoEntries() {
        when(insuranceEntryRepository.getOldestInsuranceEntryDate())
                .thenReturn(java.util.Optional.empty());

        java.util.List<String> years = insuranceService.getYears();

        assertThat(years).contains(String.valueOf(LocalDate.now().getYear()));
    }

    // --- getStates ---

    @Test
    void getStates_returnsAllStatesOrdered() {
        java.util.List<InsuranceEntryState> states = insuranceService.getStates();

        assertThat(states).isNotEmpty();
        assertThat(states).containsAll(java.util.Arrays.asList(InsuranceEntryState.values()));
    }

    // --- getEntry ---

    @Test
    void getEntry_existingId_returnsDTO() {
        InsurancePerson person = new InsurancePerson();
        person.setName("Alice");
        InsuranceEntry entry = new InsuranceEntry();
        entry.setEntryDate(LocalDate.now());
        entry.setPerson(person);
        entry.setType(InsuranceEntryType.DOCTOR);
        entry.setInsuranceType(InsuranceType.HEALTH);
        entry.setName("Dr. Smith");
        entry.setState(InsuranceEntryState.RECORDED);
        entry.setAmount(new BigDecimal("100"));

        when(insuranceEntryRepository.findById(1L)).thenReturn(java.util.Optional.of(entry));

        InsuranceEntryDTO dto = insuranceService.getEntry(1L);

        assertThat(dto).isNotNull();
    }
}

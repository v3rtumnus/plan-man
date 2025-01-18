package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.InsuranceEntryRepository;
import at.v3rtumnus.planman.dao.InsurancePersonRepository;
import at.v3rtumnus.planman.dto.insurance.InsuranceEntryDTO;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntry;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.entity.insurance.InsurancePerson;
import at.v3rtumnus.planman.entity.insurance.InsuranceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class InsuranceService {

    private final InsuranceEntryRepository insuranceEntryRepository;
    private final InsurancePersonRepository insurancePersonRepository;

    public void saveInsuranceEntry(InsuranceEntryDTO insuranceEntry) {
        log.info("Saving insurance entry");

        InsurancePerson person = insurancePersonRepository.findByName(insuranceEntry.getPerson())
                .orElseThrow(() -> new RuntimeException("Person not found"));

        insuranceEntryRepository.saveAndFlush(new InsuranceEntry(insuranceEntry.getEntryDate(),
                person, insuranceEntry.getType(), insuranceEntry.getInsuranceType(), insuranceEntry.getDoctor(), insuranceEntry.getState(),
                insuranceEntry.getAmount(), insuranceEntry.getInvoiceFilename(), insuranceEntry.getInvoiceData()));

        log.info("Insurance entry successfully saved");
    }

    public List<String> getPersons() {
        return insurancePersonRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(InsurancePerson::getName))
                .map(InsurancePerson::getName)
                .toList();
    }
    public List<String> getYears() {
        LocalDate oldestEntryDate = insuranceEntryRepository.getOldestInsuranceEntryDate().orElse(LocalDate.now());
        List<String> years = new java.util.ArrayList<>(Collections.emptyList());

        for (int i = LocalDate.now().getYear(); i >= oldestEntryDate.getYear(); i--) {
            years.add("" + i);
        }

        return years;
    }

    public List<InsuranceEntryState> getStates() {
        return Arrays
                .stream(InsuranceEntryState.values())
                .sorted(Comparator.comparing(InsuranceEntryState::getOrder))
                .toList();
    }

    public List<InsuranceEntryDTO> getInsuranceEntries(String year, String person, InsuranceEntryState insuranceEntryState) {
        return insuranceEntryRepository.getFilteredInsuranceEntries(year, person, insuranceEntryState)
                .stream().peek(e -> {
                    if (e.getName().isEmpty()) {
                        e.setName("Apotheke");
                    }
                })
                .map(InsuranceEntryDTO::fromInsuranceEntry)
                .toList();
    }

    public InsuranceEntryDTO getEntry(Long id) {
        return InsuranceEntryDTO.fromInsuranceEntry(
                insuranceEntryRepository.findById(id).orElseThrow(() -> new RuntimeException("Entry not found")));
    }

    @Transactional
    public void updateState(Long id, InsuranceEntryState state) {
        insuranceEntryRepository.updateState(id, state);
    }

    @Transactional
    public void updateState(Long id, InsuranceEntryState state, BigDecimal amount, String fileName, byte[] fileData) {
        if (state == InsuranceEntryState.HEALH_INSURANCE_RECEIVED) {
            insuranceEntryRepository.updateStateWithHealthInsurance(id, state, amount, fileName, fileData);
        } else {
            insuranceEntryRepository.updateStateWithPrivateInsurance(id, state, amount, fileName, fileData);
        }
    }

    @Transactional
    public void updateAmountReceived(Long id, InsuranceType insuranceType) {
        if (insuranceType == InsuranceType.HEALTH) {
            insuranceEntryRepository.updateHealthAmountReceived(id);

        } else {
            insuranceEntryRepository.updatePrivateAmountReceived(id);
        }
    }
}

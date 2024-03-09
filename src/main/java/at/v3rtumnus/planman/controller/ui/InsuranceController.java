package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.dto.expense.ExpenseGraphItem;
import at.v3rtumnus.planman.dto.expense.ExpenseSummary;
import at.v3rtumnus.planman.dto.insurance.InsuranceEntryDTO;
import at.v3rtumnus.planman.entity.expense.ExpenseCategory;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryType;
import at.v3rtumnus.planman.entity.insurance.InsurancePerson;
import at.v3rtumnus.planman.service.ExpenseService;
import at.v3rtumnus.planman.service.InsuranceService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.websocket.server.PathParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/insurance")
@Slf4j
@AllArgsConstructor
public class InsuranceController {

    private final InsuranceService insuranceService;

    @GetMapping(path = "/overview")
    public ModelAndView getInsuranceOverview() {
        return renderOverview(false, false);
    }
    @PostMapping
    public ModelAndView submit(   @RequestParam("invoice") MultipartFile invoice,
                            @RequestParam("date") LocalDate date,
                            @RequestParam("person") String person,
                            @RequestParam("doctor") String doctor,
                            @RequestParam("amount") BigDecimal amount) {
        try {
            InsuranceEntryDTO newEntry = new InsuranceEntryDTO(
                    date, person, doctor.isEmpty() ? InsuranceEntryType.PHARMACY : InsuranceEntryType.DOCTOR,
                    doctor, InsuranceEntryState.RECORDED, amount, invoice.getOriginalFilename(), invoice.getBytes()
            );

            insuranceService.saveInsuranceEntry(newEntry);
        } catch (Exception e) {
            log.error("An error occured handling a save-insurance entry request", e);
            return renderOverview(false, true);
        }

        return renderOverview(true, false);
    }

    @GetMapping(path = "/table")
    public ModelAndView getEntriesTable(@RequestParam(value = "year", required = false) String year,
                                                  @RequestParam(value = "person", required = false) String person,
                                                  @RequestParam(value = "state", required = false) String state) {
        ModelAndView modelAndView = new ModelAndView("fragments/insurance_table");

        List<InsuranceEntryDTO> insuranceEntries = insuranceService.getInsuranceEntries(!year.equals("ALL") ? year : null,
                !person.equals("ALL") ? person : null,
                !state.equals("ALL") ? InsuranceEntryState.fromString(state) : null);

        modelAndView.addObject("entries", insuranceEntries);

        double sumInvoice = insuranceEntries
                .stream()
                .mapToDouble(e -> e.getAmount().doubleValue())
                .sum();

        double sumHealthInsurance = insuranceEntries
                .stream()
                .mapToDouble(e -> e.getHealthInsuranceAmount() == null ? 0.0 : e.getHealthInsuranceAmount().doubleValue())
                .sum();

        double sumPrivateInsurance = insuranceEntries
                .stream()
                .mapToDouble(e -> e.getPrivateInsuranceAmount() == null ? 0.0 : e.getPrivateInsuranceAmount().doubleValue())
                .sum();

        double sumRetention = insuranceEntries
                .stream()
                .mapToDouble(e -> e.getRetention() == null ? 0.0 : e.getRetention().doubleValue())
                .sum();

        modelAndView.addObject("sumInvoice", sumInvoice);
        modelAndView.addObject("sumHealthInsurance", sumHealthInsurance);
        modelAndView.addObject("sumPrivateInsurance", sumPrivateInsurance);
        modelAndView.addObject("sumRetention", sumRetention);

        return modelAndView;
    }

    @GetMapping(path = "/file/{type}/{id}")
    public void getExpensesMonthlyDetails(@PathVariable(value = "type", required = false) String type,
                                                  @PathVariable(value = "id", required = false) Long id,
                                                  HttpServletResponse response) {

        try {
            InsuranceEntryDTO entry = insuranceService.getEntry(id);

            byte[] content = null;
            String filename = null;

            switch (type) {
                case "invoice" -> {
                    content = entry.getInvoiceData();
                    filename = entry.getInvoiceFilename();
                }
                case "health" -> {
                    content = entry.getHealthInsuranceData();
                    filename = entry.getHealthInsuranceFilename();
                }
                case "private" -> {
                    content = entry.getPrivateInsuranceData();
                    filename = entry.getPrivateInsuranceFilename();
                }
            }

            response.setContentType("application/pdf");
            response.setContentLength(content.length);

            String headerKey = "Content-Disposition";
            String headerValue = String.format("attachment; filename=\"%s\"",
                    filename);
            response.setHeader(headerKey, headerValue);

            OutputStream outStream = response.getOutputStream();
            outStream.write(content);
            outStream.close();
        } catch (Exception e) {
            log.error("An exception occured during download", e);
            throw new RuntimeException("An exception occured during download");
        }
    }

    private ModelAndView renderOverview(boolean success, boolean error) {
        ModelAndView modelAndView = new ModelAndView("insurance/overview");

        modelAndView.addObject("persons", insuranceService.getPersons());
        modelAndView.addObject("years", insuranceService.getYears());
        modelAndView.addObject("states", insuranceService.getStates());
        modelAndView.addObject("success", success);
        modelAndView.addObject("error", error);

        return modelAndView;
    }
}

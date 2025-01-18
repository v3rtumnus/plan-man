package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.entity.insurance.InsuranceType;
import at.v3rtumnus.planman.service.InsuranceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Controller
@RequestMapping("/api/insurance")
@Slf4j
@AllArgsConstructor
public class InsuranceApiController {

    private final InsuranceService insuranceService;

    @PutMapping(path = "{id}")
    public @ResponseBody void editInsuranceEntry(@PathVariable("id") Long id,
                                                 @RequestParam("currentState") String stateString,
                                                 @RequestParam(value = "amount", required = false) BigDecimal amount,
                                                 @RequestParam(value = "file", required = false) MultipartFile file) {
        InsuranceEntryState state = InsuranceEntryState.fromString(stateString);

        try {
            switch (state) {
                case RECORDED -> insuranceService.updateState(id, InsuranceEntryState.WAITING_FOR_HEALTH_INSURANCE);
                case WAITING_FOR_HEALTH_INSURANCE -> insuranceService.updateState(id, InsuranceEntryState.HEALH_INSURANCE_RECEIVED, amount, file.getOriginalFilename(), file.getBytes());
                case HEALH_INSURANCE_RECEIVED -> insuranceService.updateState(id, InsuranceEntryState.WAITING_FOR_PRIVATE_INSURANCE);
                case WAITING_FOR_PRIVATE_INSURANCE -> insuranceService.updateState(id, InsuranceEntryState.DONE, amount, file.getOriginalFilename(), file.getBytes());
                case DONE -> log.warn("Cannot edit insurance entry already marked as done");
            }
        } catch (Exception e) {
            log.error("Error while editing insurance entry with id {}", id, e);
            throw new RuntimeException(e);
        }
    }

    @PutMapping(path = "/amount/{id}")
    public @ResponseBody void insuranceAmountReceived(@PathVariable("id") Long id,
                                                 @RequestParam("type") InsuranceType type) {
        try {
            insuranceService.updateAmountReceived(id, type);
        } catch (Exception e) {
            log.error("Error while updating amount received for insurance entry with id {}", id, e);
            throw new RuntimeException(e);
        }
    }
}

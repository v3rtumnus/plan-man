package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import at.v3rtumnus.planman.service.BalanceService;
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
            log.error("Error while editing insurance entry");
            throw new RuntimeException(e);
        }
    }
}

package at.v3rtumnus.planman.controller.api;

import at.v3rtumnus.planman.dto.balance.NewBalanceItemDto;
import at.v3rtumnus.planman.dto.expense.ExpenseDTO;
import at.v3rtumnus.planman.service.BalanceService;
import at.v3rtumnus.planman.service.ExpenseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/balance")
@Slf4j
@AllArgsConstructor
public class BalanceApiController {

    private final BalanceService balanceService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody void saveBalanceItem(@RequestBody NewBalanceItemDto balanceItem) {
        balanceService.saveBalanceItem(balanceItem);
    }
}

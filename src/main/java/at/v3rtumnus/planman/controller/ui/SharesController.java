package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.dto.credit.CreditPlanRow;
import at.v3rtumnus.planman.dto.credit.Payment;
import at.v3rtumnus.planman.dto.credit.RowType;
import at.v3rtumnus.planman.entity.credit.CreditSingleTransaction;
import at.v3rtumnus.planman.service.CreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/shares")
@Slf4j
public class SharesController {

    @Autowired
    private CreditService creditService;

    @GetMapping(path = "/upload")
    public ModelAndView getCreditPlan() {
        ModelAndView modelAndView = new ModelAndView("shares/upload");

        //modelAndView.addObject("currentCreditPlan", creditService.generateCurrentCreditPlan());

        return modelAndView;
    }
}

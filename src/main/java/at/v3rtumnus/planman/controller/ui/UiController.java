package at.v3rtumnus.planman.controller.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/")
public class UiController {

    @GetMapping("")
    public RedirectView homePage(Model model) {
        return new RedirectView("expenses");
    }
}

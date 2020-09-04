package at.v3rtumnus.planman.rest;

import at.v3rtumnus.planman.dto.PlanManUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class UiController {

    @GetMapping("")
    public String homePage(Model model) {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @ModelAttribute
    public User globalUserObject(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("loggedInUser", authentication.getName());
        model.addAttribute("roles", authentication.getAuthorities());

        return new PlanManUser(authentication.getName(), "", authentication.getAuthorities());
    }

}

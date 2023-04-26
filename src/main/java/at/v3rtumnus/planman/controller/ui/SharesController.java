package at.v3rtumnus.planman.controller.ui;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/shares")
@Slf4j
@AllArgsConstructor
public class SharesController {

    @GetMapping(path = "/upload")
    public ModelAndView getShareUpload() {
        return new ModelAndView("shares/upload");
    }
}

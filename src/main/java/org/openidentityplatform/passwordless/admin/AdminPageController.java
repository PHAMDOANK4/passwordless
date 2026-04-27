package org.openidentityplatform.passwordless.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    @GetMapping({"/admin", "/admin/"})
    public String adminIndex() {
        return "redirect:/admin/dashboard";
    }
}

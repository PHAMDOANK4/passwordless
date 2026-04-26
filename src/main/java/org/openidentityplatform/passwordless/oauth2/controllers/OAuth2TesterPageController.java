package org.openidentityplatform.passwordless.oauth2.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OAuth2TesterPageController {

    @GetMapping({"/oauth2-tester", "/oauth2-tester/"})
    public String oauth2Tester() {
        return "redirect:/oauth2-tester/index.html";
    }
}

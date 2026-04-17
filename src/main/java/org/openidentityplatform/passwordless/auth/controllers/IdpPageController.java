package org.openidentityplatform.passwordless.auth.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IdpPageController {

    @GetMapping({"/idp", "/idp/"})
    public String idpIndex() {
        return "redirect:/idp/index.html";
    }
}

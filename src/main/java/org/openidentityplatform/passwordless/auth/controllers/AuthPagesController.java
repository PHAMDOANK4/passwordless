package org.openidentityplatform.passwordless.auth.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthPagesController {

    @GetMapping({"/login", "/login/"})
    public String loginPage() {
        return "redirect:/login/index.html";
    }

    @GetMapping({"/register", "/register/"})
    public String registerPage() {
        return "redirect:/register/index.html";
    }

    @GetMapping({"/verify-otp", "/verify-otp/"})
    public String verifyOtpPage() {
        return "redirect:/verify-otp/index.html";
    }

    @GetMapping({"/setup-auth-methods", "/setup-auth-methods/"})
    public String setupAuthMethodsPage() {
        return "redirect:/setup-auth-methods/index.html";
    }
}

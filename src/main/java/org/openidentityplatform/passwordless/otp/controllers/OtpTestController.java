package org.openidentityplatform.passwordless.otp.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/otp/test")
public class OtpTestController {

    @GetMapping("")
    public String index() {
        return "otp-test";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "otp-register";
    }
}

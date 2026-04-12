package org.openidentityplatform.passwordless.totp.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/totp/test")
public class TotpTestController {

    @GetMapping("")
    public String index() {
        return "totp-test";
    }
}

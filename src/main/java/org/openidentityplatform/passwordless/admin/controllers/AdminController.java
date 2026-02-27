package org.openidentityplatform.passwordless.admin.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("")
    public String dashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/domains")
    public String domains() {
        return "admin/domains";
    }

    @GetMapping("/users")
    public String users() {
        return "admin/users";
    }

    @GetMapping("/apps")
    public String apps() {
        return "admin/apps";
    }

    @GetMapping("/audit")
    public String audit() {
        return "admin/audit";
    }
}

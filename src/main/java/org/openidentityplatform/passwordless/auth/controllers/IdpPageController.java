package org.openidentityplatform.passwordless.auth.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IdpPageController {

    @GetMapping({"/", "/idp", "/idp/"})
    public String idpIndex() {
        return "forward:/idp/index.html";
    }

    @GetMapping({
            "/mfa",
            "/profile",
            "/logout",
            "/admin/dashboard",
            "/admin/users",
            "/admin/clients",
            "/admin/domains",
            "/admin/system",
            "/admin/audit",
            "/admin/api-keys",
            "/developer/register-client",
            "/developer/swagger",
            "/developer/token-inspector",
            "/oauth/consent",
            "/oauth/error",
            "/error/401",
            "/error/403"
    })
    public String forwardSpaRoutes() {
        return "forward:/idp/index.html";
    }
}

package org.openidentityplatform.passwordless.auth.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = IdpPageController.class, properties = "security.api-key-filter.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
class IdpPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void idpRoute_redirectsToStaticIndex() throws Exception {
        mockMvc.perform(get("/idp"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/idp/index.html"));
    }

    @Test
    void idpRouteWithTrailingSlash_redirectsToStaticIndex() throws Exception {
        mockMvc.perform(get("/idp/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/idp/index.html"));
    }
}

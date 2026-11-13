package ca.vina.nexu.partage.web;

import ca.vina.nexu.ClientHttpEssai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Sonde de vie — sans session")
class ControleurSanteTest {

    @Test
    @DisplayName("GET /api/health répond 200 { status: ok } sans session")
    void sante() throws Exception {
        new ClientHttpEssai(new ControleurSante()).get("/api/health", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }
}

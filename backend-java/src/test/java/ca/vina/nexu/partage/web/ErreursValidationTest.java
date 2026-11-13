package ca.vina.nexu.partage.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Erreurs de validation — la forme du Node, un ordre stable")
class ErreursValidationTest {

    record Commande(@NotBlank(message = "b requis") String b, @NotBlank(message = "a requis") String a) { }

    @Test
    void rangSuitLaDeclarationDuRecord() {
        assertThat(ErreursValidation.rang(Commande.class, "b")).isZero();
        assertThat(ErreursValidation.rang(Commande.class, "a")).isEqualTo(1);
        assertThat(ErreursValidation.rang(Commande.class, "z")).isEqualTo(Integer.MAX_VALUE);
        assertThat(ErreursValidation.rang(String.class, "a")).isEqualTo(Integer.MAX_VALUE);
        assertThat(ErreursValidation.rang(null, "a")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void contraintesEtTypes() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Commande>> violations = validator.validate(new Commande(" ", null));
        var reponse = new ErreursValidation().contraintesViolees(new ConstraintViolationException(violations));
        assertThat(reponse.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<String, List<String>>) reponse.getBody()).get("errors")).containsExactly("a requis; b requis");

        var type = new ErreursValidation().typeInattendu(new MethodArgumentTypeMismatchException("x", Integer.class, "n", null, null));
        assertThat(((Map<String, List<String>>) type.getBody()).get("errors")).containsExactly("paramètre « n » d'un type inattendu");
    }

    /** Un contrôleur qui emploie la validation déclarative : un corps {@code @Valid}, un paramètre contraint, un corps et un paramètre ensemble. */
    @org.springframework.web.bind.annotation.RestController
    static class ControleurValide {
        @org.springframework.web.bind.annotation.PostMapping("/essai/corps")
        public String corps(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody Commande c) {
            return "ok";
        }

        @org.springframework.web.bind.annotation.GetMapping("/essai/parametre")
        public String parametre(@org.springframework.web.bind.annotation.RequestParam @jakarta.validation.constraints.Min(value = 1, message = "n au moins 1") int n) {
            return "ok";
        }

        @org.springframework.web.bind.annotation.PostMapping("/essai/ensemble")
        public String ensemble(@org.springframework.web.bind.annotation.RequestParam @jakarta.validation.constraints.Min(value = 1, message = "n au moins 1") int n,
                               @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody Commande c) {
            return "ok";
        }
    }

    @Test
    @DisplayName("un corps @Valid refusé : les messages dans l'ordre des champs du record ; un paramètre contraint : son message ; les deux ensemble : tout")
    void parLeRouteur() throws Exception {
        ca.vina.nexu.ClientHttpEssai http = new ca.vina.nexu.ClientHttpEssai(new ControleurValide());
        http.post("/essai/corps", null, "{\"b\":\" \",\"a\":\"\"}").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errors[0]").value("b requis; a requis"));
        http.get("/essai/parametre?n=0", null).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errors[0]").value("n au moins 1"));
        http.post("/essai/ensemble?n=0", null, "{\"b\":\"x\",\"a\":\" \"}").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errors[0]").value("n au moins 1; a requis"));
        http.post("/essai/corps", null, "{pas du json").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errors[0]").value("corps de requête illisible : JSON attendu"));
    }
}

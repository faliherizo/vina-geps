package ca.vina.nexu.ordonnanceur.infrastructure.web;

import ca.vina.nexu.ordonnanceur.infrastructure.configuration.ConfigurationOrdonnanceur;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de la visibilité de l'ordonnanceur : lecture réservée à l'administration, tic à la demande. */
@DisplayName("API ordonnanceur — tâches visibles et tic à la demande")
class ControleurOrdonnanceurHttpTest {

    @Test
    void lectureReserveeEtTicALaDemande(@TempDir Path dossier) throws Exception {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        RegistreExecuteurs registre = new RegistreExecuteurs();
        registre.enregistrer("essai", (t, maintenant) -> Optional.empty());
        DepotTaches taches = new DepotTaches(jdbc, registre);
        ConfigurationOrdonnanceur config = new ConfigurationOrdonnanceur();
        ClientHttpEssai http = new ClientHttpEssai(new ControleurOrdonnanceur(config.lectureTaches(taches), config.declencherTic(taches, () -> "2026-09-15T12:00:00.000Z"), new GardeAcces(new RegistrePolitiqueAcces())));
        taches.planifier("t-1", "essai", "c-1", "2020-01-01T00:00:00.000Z", Map.of("n", 1), "essai");
        taches.planifier("t-1", "autre", "c-2", "2999-01-01T00:00:00.000Z", Map.of(), "essai");
        taches.planifier("t-2", "essai", "c-3", "2020-01-01T00:00:00.000Z", Map.of(), "essai");
        http.get("/api/ordonnanceur/taches", null).andExpect(status().isUnauthorized());
        http.get("/api/ordonnanceur/taches", ClientHttpEssai.session("employee", "t-1")).andExpect(status().isForbidden());
        http.get("/api/ordonnanceur/taches", ClientHttpEssai.session("admin", "t-1")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].cibleId").value("c-1")).andExpect(jsonPath("$[0].statut").value("en_attente")).andExpect(jsonPath("$[0].charge.n").value(1));
        http.get("/api/ordonnanceur/taches?type=autre", ClientHttpEssai.session("admin", "t-1")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].cibleId").value("c-2"));
        http.post("/api/ordonnanceur/tic", ClientHttpEssai.session("employee", "t-1"), null).andExpect(status().isForbidden());
        // Le tic prend ce qui est échu, tous établissements confondus : deux tâches faites, la future attend.
        http.post("/api/ordonnanceur/tic", ClientHttpEssai.session("admin", "t-1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.prises").value(2)).andExpect(jsonPath("$.faites").value(2)).andExpect(jsonPath("$.echouees").value(0));
        http.get("/api/ordonnanceur/taches?statut=faite", ClientHttpEssai.session("admin", "t-1")).andExpect(jsonPath("$", hasSize(1)));
        http.get("/api/ordonnanceur/taches?statut=en_attente", ClientHttpEssai.session("admin", "t-1")).andExpect(jsonPath("$", hasSize(1)));
    }
}

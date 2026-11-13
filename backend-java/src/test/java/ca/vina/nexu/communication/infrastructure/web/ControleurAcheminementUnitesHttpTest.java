package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.communication.application.requetes.AcheminementParUnite;
import ca.vina.nexu.communication.domaine.AcheminementPort;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.infrastructure.configuration.ConfigurationCommunication;
import ca.vina.nexu.communication.infrastructure.persistance.DepotReglagesAcheminement;
import ca.vina.nexu.communication.infrastructure.persistance.JournalCommunication;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/communication/acheminement/unites} : lire est ouvert à l'établissement, régler demande
 * la gestion des effectifs ; un réglage change par où la messagerie joint les membres de l'unité.
 */
@DisplayName("API acheminement par unité — lecture ouverte, réglage gardé, résolution par l'unité de la personne")
class ControleurAcheminementUnitesHttpTest {

    private static final String T = "T1";
    private static final String R = "/api/communication/acheminement/unites";
    private static final ContexteRequete GESTION = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete EMPLOYE = new ContexteRequete("u-ngu", "ngu", "employee", "ngu", T);
    private static final ContexteRequete SANS_MODULE = ClientHttpEssai.session("admin", "T2");

    /** Deux unités : Est (Roy, Ngu) et Ouest (Tremblay). L'implémentation par défaut de {@code groupesDe} est éprouvée ici. */
    private static final GroupesDeTravailPort GROUPES = new GroupesDeTravailPort() {
        @Override
        public List<Groupe> groupes(String tenantId) {
            return List.of(new Groupe("EST", "EST", "Secteur Est"), new Groupe("OUEST", "OUEST", "Secteur Ouest"));
        }

        @Override
        public List<Membre> membres(String tenantId, String groupeId) {
            return "EST".equals(groupeId) ? List.of(new Membre("roy", "Julie Roy"), new Membre("ngu", "Ngu")) : List.of(new Membre("tre", "Tremblay"));
        }
    };

    private ClientHttpEssai http;
    private JdbcClient jdbc;
    private AcheminementPort acheminement;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, 1, 1, '[\"effectifs_communication\"]')").params(T).update();
        DepotReglagesAcheminement depot = new DepotReglagesAcheminement(jdbc);
        ConfigurationCommunication c = new ConfigurationCommunication();
        Clock horloge = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
        acheminement = c.acheminement(GROUPES, depot);
        GardeCommunication garde = new GardeCommunication(new GardeAcces(new RegistrePolitiqueAcces()), new DepotModulesActives(jdbc));
        http = new ClientHttpEssai(new ControleurAcheminementUnites(garde, c.lectureReglagesAcheminement(GROUPES, depot),
                c.gestionnaireReglagesAcheminement(GROUPES, depot, new JournalCommunication(jdbc), horloge)));
    }

    @Test
    @DisplayName("chaque unité est listée avec ses canaux et leur origine, et l'écran reçoit les nomenclatures")
    void lecture() throws Exception {
        http.get(R, null).andExpect(status().isUnauthorized());
        http.get(R, SANS_MODULE).andExpect(status().isForbidden());
        http.get(R, EMPLOYE).andExpect(status().isOk())
                .andExpect(jsonPath("$.unites.length()").value(2)).andExpect(jsonPath("$.unites[0].uniteId").value("EST"))
                .andExpect(jsonPath("$.unites[0].nom").value("Secteur Est")).andExpect(jsonPath("$.unites[0].parDefaut").value(true))
                .andExpect(jsonPath("$.unites[0].canaux[0].canal").value("courriel")).andExpect(jsonPath("$.unites[0].canaux[0].libelle").value("Courriel — Sonnerie")).andExpect(jsonPath("$.unites[0].modifieLe").isEmpty())
                .andExpect(jsonPath("$.canaux.length()").value(6)).andExpect(jsonPath("$.canaux[1].code").value("sms")).andExpect(jsonPath("$.canaux[1].libelle").value("Texto (SMS)"))
                .andExpect(jsonPath("$.modes.length()").value(5)).andExpect(jsonPath("$.modes[0].code").value("sonnerie"));
    }

    @Test
    @DisplayName("régler une unité change l'acheminement de ses membres, et seulement d'eux ; rétablir rend le défaut")
    void reglerPuisRetablir() throws Exception {
        String corps = "{\"etapes\":[{\"canal\":\"sms\",\"mode\":\"prioritaire\"},{\"canal\":\"appel\"}]}";
        http.put(R + "/EST", EMPLOYE, corps).andExpect(status().isForbidden()).andExpect(jsonPath("$.capability").value("effectifs.manage"));
        http.put(R + "/EST", GESTION, corps).andExpect(status().isOk())
                .andExpect(jsonPath("$.uniteId").value("EST")).andExpect(jsonPath("$.etapes[1].mode").value("sonnerie"))
                .andExpect(jsonPath("$.modifieLe").value("2026-11-13T10:00:00.000Z"));
        assertThat(acheminement.acheminement(T, "ngu", "message")).extracting(AcheminementPort.Canal::canal).containsExactly("sms", "appel");
        assertThat(acheminement.acheminement(T, "tre", "message")).extracting(AcheminementPort.Canal::parDefaut).containsExactly(true);
        assertThat(acheminement.acheminement(T, "inconnu", "message")).extracting(AcheminementPort.Canal::canal).containsExactly("courriel");
        http.get(R, EMPLOYE).andExpect(jsonPath("$.unites[0].parDefaut").value(false)).andExpect(jsonPath("$.unites[0].canaux[0].ordrePriorite").value(1))
                .andExpect(jsonPath("$.unites[0].modifiePar").value("admin@t"));
        // Un second réglage remplace le premier en entier.
        http.put(R + "/EST", GESTION, "{\"etapes\":[{\"canal\":\"pagette\",\"mode\":\"vibration\"}]}").andExpect(status().isOk());
        assertThat(acheminement.acheminement(T, "roy", "message")).containsExactly(new AcheminementPort.Canal("pagette", "vibration", 1, false));
        assertThat(jdbc.sql("SELECT action FROM msg_journal ORDER BY horodatage").query(String.class).list()).containsOnly("acheminement.reglage");

        http.delete(R + "/EST", EMPLOYE).andExpect(status().isForbidden());
        http.delete(R + "/EST", GESTION).andExpect(status().isOk()).andExpect(jsonPath("$.parDefaut").value(true));
        http.delete(R + "/EST", GESTION).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("cette unité suit déjà l'acheminement par défaut"));
        assertThat(acheminement.acheminement(T, "roy", "message")).extracting(AcheminementPort.Canal::parDefaut).containsExactly(true);
    }

    @Test
    @DisplayName("une unité inconnue, un canal inconnu, une chaîne vide ou répétée sont refusés nommément")
    void refus() throws Exception {
        http.put(R + "/NORD", GESTION, "{\"etapes\":[{\"canal\":\"sms\"}]}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("unité introuvable"));
        http.delete(R + "/NORD", GESTION).andExpect(status().isNotFound());
        http.put(R + "/EST", GESTION, "{\"etapes\":[{\"canal\":\"pigeon\"}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("canal inconnu « pigeon »")));
        http.put(R + "/EST", GESTION, "{\"etapes\":[{\"canal\":\"sms\",\"mode\":\"fort\"}]}").andExpect(status().isBadRequest());
        http.put(R + "/EST", GESTION, "{\"etapes\":[{\"canal\":7}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("canal inconnu « null »")));
        http.put(R + "/EST", GESTION, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("au moins un canal est requis"));
        http.put(R + "/EST", GESTION, "{\"etapes\":[\"sms\"]}").andExpect(status().isBadRequest());
        http.put(R + "/EST", GESTION, "{\"etapes\":[{\"canal\":\"sms\"},{\"canal\":\"sms\",\"mode\":\"vibration\"}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("un canal est répété"));
        assertThat(ControleurAcheminementUnites.etapes(Map.of("etapes", "x"))).isEmpty();
    }
}

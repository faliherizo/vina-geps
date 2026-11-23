package ca.vina.nexu.notifications.domaine;

import ca.vina.nexu.notifications.domaine.Evenements.Canaux;
import ca.vina.nexu.notifications.domaine.Evenements.Evenement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le catalogue des avis, les préférences par événement, les textes (ADR 0029) — les mêmes codes et les mêmes refus que le Node. */
@DisplayName("Avis d'événements — catalogue, préférences par événement, textes")
class EvenementsTest {

    @Test
    @DisplayName("le catalogue : codes uniques, module libellé, un canal écrit par défaut ; chaque code est émis par le code de production")
    void catalogue() throws Exception {
        assertThat(Evenements.TOUS.stream().map(Evenement::code).distinct()).hasSize(Evenements.TOUS.size());
        for (Evenement e : Evenements.TOUS) {
            assertThat(Evenements.LIBELLE_MODULE).containsKey(e.module());
            assertThat(e.defauts().courriel() || e.defauts().texto()).as(e.code()).isTrue();
            assertThat(e.vue()).containsEntry("moduleLibelle", Evenements.LIBELLE_MODULE.get(e.module())).containsKey("defauts");
        }
        assertThat(Evenements.parCode("inconnu")).isEmpty();
        Path racine = Path.of("src/main/java/ca/vina/nexu");
        StringBuilder corpus = new StringBuilder();
        try (Stream<Path> fichiers = Files.walk(racine)) {
            for (Path p : fichiers.filter(f -> f.toString().endsWith(".java") && !f.endsWith("Evenements.java")).toList()) {
                corpus.append(Files.readString(p));
            }
        }
        List<String> orphelins = Evenements.TOUS.stream().map(Evenement::code).filter(c -> !corpus.toString().contains("\"" + c + "\"")).toList();
        assertThat(orphelins).as("codes du catalogue jamais émis").isEmpty();
    }

    @Test
    @DisplayName("un réglage se complète par les défauts ; un refus est nommé : forme, code inconnu, valeur, essentiel sans canal écrit")
    void reglages() {
        Evenement annule = Evenements.parCode("message.direct").orElseThrow();
        assertThat(Evenements.completer(annule, Map.of("texto", false))).isEqualTo(new Canaux(true, false, true));
        assertThat(Evenements.completer(annule, Map.of("texto", "non"))).isEqualTo(annule.defauts());
        assertThat(Evenements.refusReglages(null)).isEmpty();
        assertThat(Evenements.refusReglages(List.of())).containsExactly("evenements : un objet code → canaux attendu");
        assertThat(Evenements.refusReglages(Map.of("x.y", Map.of()))).containsExactly("evenements.x.y : événement inconnu");
        Map<String, Object> nul = new HashMap<>();
        nul.put("quart.offert", null);
        assertThat(Evenements.refusReglages(nul)).containsExactly("evenements.quart.offert : canaux attendus");
        assertThat(Evenements.refusReglages(Map.of("quart.offert", Map.of("texto", "non")))).containsExactly("evenements.quart.offert.texto : vrai ou faux attendu");
        assertThat(Evenements.refusReglages(Map.of("message.direct", Map.of("courriel", false, "texto", false)))).containsExactly("Message de l'encadrement : avis essentiel, gardez le courriel ou le texto ouvert");
        assertThat(Evenements.refusReglages(Map.of("quart.offert", Map.of("courriel", false, "texto", false, "poussee", false)))).isEmpty();
        assertThat(new Canaux(true, false, true).ouvert(Canal.TEXTO)).isFalse();
        assertThat(new Canaux(true, false, true).avec(Canal.POUSSEE, false)).isEqualTo(new Canaux(true, false, false));
        assertThat(new Canaux(false, false, true).ecritOuvert()).isFalse();
        assertThat(Canal.de("pigeon")).isEmpty();
        assertThat(Canal.POUSSEE.ecrit()).isFalse();
    }

    @Test
    @DisplayName("préférences : le canal effectif combine l'interrupteur général et celui de l'événement ; la fusion garde les événements réglés ; l'essentiel effectif est refusé")
    void preferences() {
        Preferences p = Preferences.PAR_DEFAUT.fusionner(Map.of("evenements", Map.of("quart.offert", Map.of("texto", false, "poussee", false))), "t");
        assertThat(p.evenements()).containsEntry("quart.offert", new Canaux(true, false, false));
        assertThat(p.ouvert("poussee", "quart.offert")).isFalse();
        assertThat(p.ouvert("texto", "quart.offert")).isFalse();
        assertThat(p.ouvert("texto", "quart.attribue")).isTrue();
        assertThat(p.ouvert("texto", null)).isTrue();
        assertThat(p.ouvert("texto", "inconnu")).isTrue();
        Preferences ferme = p.fusionner(Map.of("texto", false), "t2");
        assertThat(ferme.ouvert("texto", "quart.attribue")).isFalse();
        assertThat(ferme.evenements()).containsKey("quart.offert");
        Preferences deux = ferme.fusionner(Map.of("evenements", Map.of("quart.attribue", Map.of("courriel", false))), "t3");
        assertThat(deux.evenements().keySet()).containsExactlyInAnyOrder("quart.offert", "quart.attribue");
        assertThatThrownBy(() -> ferme.fusionner(Map.of("evenements", Map.of("message.direct", Map.of("courriel", false))), "t")).hasMessage("Message de l'encadrement : avis essentiel, gardez le courriel ou le texto ouvert");
        assertThatThrownBy(() -> Preferences.PAR_DEFAUT.fusionner(Map.of("evenements", Map.of("message.direct", Map.of("courriel", false, "texto", false))), "t")).hasMessageContaining("essentiel");
        assertThatThrownBy(() -> Preferences.PAR_DEFAUT.fusionner(Map.of("evenements", "x"), "t")).hasMessage("evenements : un objet code → canaux attendu");
        assertThatThrownBy(() -> Preferences.PAR_DEFAUT.ouvert("pigeon")).hasMessage("canal inconnu : pigeon");
        assertThat(p.vue()).containsKey("evenements");
        assertThat(((Map<?, ?>) p.vue().get("evenements")).get("quart.offert")).isEqualTo(Map.of("courriel", true, "texto", false, "poussee", false));
    }

    @Test
    @DisplayName("un avis obligatoire (l'alerte critique) passe outre chaque préférence fermée ; un avis réglable la respecte (critère 22)")
    void obligatoirePasseOutreLesPreferences() {
        Preferences ferme = Preferences.PAR_DEFAUT.fusionner(Map.of("texto", false, "poussee", false), "t")
                .fusionner(Map.of("evenements", Map.of(Evenements.ALERTE_CRITIQUE, Map.of("texto", false, "poussee", false))), "t2");
        for (String canal : List.of("courriel", "texto", "poussee")) {
            assertThat(ferme.ouvert(canal, Evenements.ALERTE_CRITIQUE)).as(canal).isTrue();
        }
        assertThat(ferme.ouvert("texto", "alerte.recue")).as("l'alerte ordinaire suit la préférence").isFalse();
        assertThat(ferme.ouvert("poussee", "quart.offert")).isFalse();
        assertThat(Evenements.TOUS.stream().filter(Evenement::obligatoire).map(Evenement::code)).containsExactly(Evenements.ALERTE_CRITIQUE);
        assertThat(Evenements.parCode(Evenements.ALERTE_CRITIQUE).orElseThrow().vue()).containsEntry("obligatoire", true);
        assertThat(Evenements.parCode("quart.offert").orElseThrow().vue()).containsEntry("obligatoire", false);
    }

    @Test
    @DisplayName("le courriel dit qui envoie, pourquoi et où régler ; le texto porte l'identité, le retrait, et tient dans la limite ; l'envoi manuel se refuse nommément")
    void textes() {
        String pied = Avis.piedDeCourriel("CSS des Rives", Evenements.parCode("quart.offert").orElseThrow(), "https://nexu.exemple", "/admin/timesheets");
        assertThat(pied).contains("https://nexu.exemple/admin/timesheets").contains("Nexu VINA pour CSS des Rives").contains("« Quart disponible offert »").contains("Mon compte › Mes notifications");
        String court = Avis.texteDuTexto("CSS des Rives", "Votre demande est approuvée.");
        assertThat(court).startsWith("CSS des Rives : Votre demande est approuvée.").contains("STOP/ARRET");
        String longue = Avis.texteDuTexto("CSS des Rives", "x".repeat(1000));
        assertThat(longue.length()).isLessThanOrEqualTo(Textos.LONGUEUR_MAX);
        assertThat(longue).contains("…").endsWith("pour ne plus recevoir de textos.");
        assertThat(Avis.refusEnvoiDirect(null)).containsExactly("employeeIds : au moins un identifiant d'employé", "sujet requis", "corps requis");
        assertThat(Avis.refusEnvoiDirect(Map.of("employeeIds", List.of("emp-1"), "sujet", "x".repeat(201), "corps", "y".repeat(2001), "canaux", List.of())))
                .containsExactly("sujet : 200 caractères au plus", "corps : 2 000 caractères au plus", "canaux : au moins un canal");
        assertThat(Avis.refusEnvoiDirect(Map.of("employeeIds", List.of(""), "sujet", "s", "corps", "c", "canaux", List.of("pigeon"))))
                .containsExactly("employeeIds : au moins un identifiant d'employé", "canaux : courriel, texto, poussee attendus");
        assertThat(Avis.refusEnvoiDirect(Map.of("employeeIds", List.of("emp-1"), "sujet", "s", "corps", "c", "canaux", List.of("texto")))).isEmpty();
    }
}

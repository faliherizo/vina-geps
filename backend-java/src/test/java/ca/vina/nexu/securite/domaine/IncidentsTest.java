package ca.vina.nexu.securite.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Port fidèle de `securite-incidents.test.ts` : mêmes cas, mêmes messages. */
@DisplayName("Incidents de confidentialité — règles pures")
class IncidentsTest {

    private static Map<String, Object> valide() {
        Map<String, Object> m = new HashMap<>();
        m.put("survenuLe", "2026-09-01"); m.put("decouvertLe", "2026-09-03"); m.put("nature", "acces_non_autorise");
        m.put("description", "Un compte de secrétariat a consulté trois dossiers hors de son école.");
        m.put("renseignements", List.of("identite", "emploi")); m.put("personnesTouchees", 3); m.put("gravite", "moderee");
        m.put("mesures", "Accès retiré, journal relu, personnes avisées.");
        return m;
    }

    private static Map<String, Object> avec(String cle, Object valeur) {
        Map<String, Object> m = valide();
        m.put(cle, valeur);
        return m;
    }

    @Test
    void unIncidentCompletPasseEtInconnuEstAdmis() {
        assertThat(Incidents.valider(valide())).isEmpty();
        assertThat(Incidents.valider(avec("personnesTouchees", null))).isEmpty();
        Map<String, Object> notifie = avec("notifieCai", true);
        notifie.put("notifieCaiLe", "2026-09-04");
        assertThat(Incidents.valider(notifie)).isEmpty();
        assertThat(Incidents.valider(avec("personnesTouchees", 2.0))).isEmpty();   // 2.0 lu du JSON : entier
    }

    @Test
    void chaqueManquementEstNomme() {
        assertThat(Incidents.valider(Map.of())).containsExactly(
                "survenuLe : date AAAA-MM-JJ requise", "decouvertLe : date AAAA-MM-JJ requise",
                "nature requise : acces_non_autorise, utilisation_non_autorisee, communication_non_autorisee, perte, autre",
                "description requise (20 à 5000 caractères) : ce qui s'est passé, pour la CAI", "renseignements visés : au moins un",
                "gravite requise : faible, moderee, elevee", "mesures prises requises (10 à 5000 caractères)");
        assertThat(Incidents.valider(avec("decouvertLe", "2026-08-31"))).containsExactly("la découverte ne peut précéder la survenue");
        assertThat(Incidents.valider(avec("renseignements", List.of("identite", "x")))).containsExactly("renseignements inconnus : x");
        assertThat(Incidents.valider(avec("renseignements", "identite"))).containsExactly("renseignements visés : au moins un");
        assertThat(Incidents.valider(avec("personnesTouchees", -1))).containsExactly("personnesTouchees : entier ≥ 0, ou null si inconnu");
        assertThat(Incidents.valider(avec("personnesTouchees", 1.5))).containsExactly("personnesTouchees : entier ≥ 0, ou null si inconnu");
        assertThat(Incidents.valider(avec("personnesTouchees", "3"))).containsExactly("personnesTouchees : entier ≥ 0, ou null si inconnu");
        assertThat(Incidents.valider(avec("description", "x".repeat(5001)))).hasSize(1);
        assertThat(Incidents.valider(avec("mesures", "x".repeat(5001)))).hasSize(1);
        assertThat(Incidents.valider(avec("survenuLe", 20260901))).contains("survenuLe : date AAAA-MM-JJ requise");
    }

    @Test
    void notifications() {
        assertThat(Incidents.validerNotifications(Map.of("notifieCai", true, "notifieCaiLe", "hier"))).containsExactly("notifieCaiLe : date AAAA-MM-JJ attendue");
        assertThat(Incidents.validerNotifications(Map.of("notifieCai", false, "notifieCaiLe", "2026-09-04"))).containsExactly("notifieCaiLe : une date sans notification déclarée");
        assertThat(Incidents.validerNotifications(Map.of("notifiePersonnes", false, "notifiePersonnesLe", "2026-09-04"))).containsExactly("notifiePersonnesLe : une date sans notification déclarée");
        Map<String, Object> ok = new HashMap<>();
        ok.put("notifieCai", true); ok.put("notifieCaiLe", null); ok.put("notifiePersonnes", true); ok.put("notifiePersonnesLe", "2026-09-05");
        assertThat(Incidents.validerNotifications(ok)).isEmpty();
        assertThat(Incidents.validerNotifications(Map.of("notifieCaiLe", ""))).isEmpty();   // vide = pas de date
    }

    @Test
    void closAttendueLibelles() {
        assertThat(Incidents.CHAMPS_MODIFIABLES_CLOS).containsExactly("notifieCai", "notifieCaiLe", "notifiePersonnes", "notifiePersonnesLe");
        assertThat(Incidents.champsInterditsSiClos(Map.of("notifieCai", true, "notifieCaiLe", "2026-09-04"))).isEmpty();
        assertThat(Incidents.champsInterditsSiClos(new java.util.LinkedHashMap<>(Map.of("description", "…")))).containsExactly("description");
        assertThat(Incidents.notificationAttendue("elevee", false, false)).isEqualTo(new Incidents.Attendue(true, true));
        assertThat(Incidents.notificationAttendue("elevee", true, false)).isEqualTo(new Incidents.Attendue(false, true));
        assertThat(Incidents.notificationAttendue("moderee", false, false)).isEqualTo(new Incidents.Attendue(false, false));
        assertThat(Incidents.libelle(Incidents.NATURES, "perte")).isEqualTo("Perte");
        assertThat(Incidents.libelleGravite("elevee")).isEqualTo("Élevée");
        assertThat(Incidents.libelle(Incidents.RENSEIGNEMENTS, "?")).isEqualTo("?");
        assertThat(Incidents.CONSERVATION_ANNEES).isEqualTo(5);
    }

    @Test
    void csvDuRegistreEtVue() {
        Incidents.Incident i = new Incidents.Incident("i1", "t1", "2026-09-01", "2026-09-03", "acces_non_autorise",
                "Un compte de secrétariat a consulté trois dossiers hors de son école.", List.of("identite", "emploi"), null, "moderee",
                "Accès retiré, journal relu, personnes avisées.", "e1", "Marie \"MC\" Côté", true, "2026-09-04", false, null, "clos", "2026-09-10",
                "2026-09-03T10:00:00.000Z", "admin");
        String csv = Incidents.registreCsv(List.of(i), Map.of("t1", "CSS des Chênes"), false);
        String[] lignes = csv.split("\r\n");
        assertThat(lignes[0]).startsWith("\uFEFF\"Survenu le\";\"Découvert le\";\"Nature\"");
        assertThat(lignes[1]).isEqualTo("\"2026-09-01\";\"2026-09-03\";\"Accès non autorisé\";\"Modérée\";\"Identité, Emploi\";\"inconnu\";\"Un compte de secrétariat a consulté trois dossiers hors de son école.\";\"Accès retiré, journal relu, personnes avisées.\";\"Marie \"\"MC\"\" Côté\";\"oui\";\"2026-09-04\";\"non\";\"\";\"clos\";\"2026-09-10\";\"2026-09-03\"");
        assertThat(Incidents.registreCsv(List.of(i), Map.of("t1", "CSS des Chênes"), true).split("\r\n")[1]).startsWith("\"CSS des Chênes\";\"2026-09-01\"");
        Incidents.Incident nu = new Incidents.Incident("i2", "t1", "2026-09-01", "2026-09-03", "perte", i.description(), List.of("identite"), 0, "elevee",
                i.mesures(), null, null, false, null, true, "2026-09-06", "ouvert", null, "2026-09-03T10:00:00.000Z", "admin");
        String ligne = Incidents.registreCsv(List.of(nu), Map.of(), true).split("\r\n")[1];
        assertThat(ligne).startsWith("\"t1\";\"2026-09-01\"").contains("\"0\";").contains("\"\";\"non\";\"\";\"oui\";\"2026-09-06\";\"ouvert\";\"\"");
        Map<String, Object> vue = Incidents.vue(nu, "CSS");
        assertThat(vue).containsEntry("nomEtablissement", "CSS").containsEntry("statut", "ouvert");
        assertThat(vue.get("attendue")).isEqualTo(Map.of("cai", true, "personnes", false));
    }
}

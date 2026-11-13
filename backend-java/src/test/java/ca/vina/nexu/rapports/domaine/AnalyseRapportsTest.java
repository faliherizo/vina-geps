package ca.vina.nexu.rapports.domaine;

import ca.vina.nexu.rapports.domaine.Periodicites.Bornes;
import ca.vina.nexu.rapports.domaine.Periodicites.Periodicite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les règles pures de l'analyse : périodicités (BF-132), utilisation (BF-130), indicateurs (BF-046). */
@DisplayName("Rapports — périodicités, utilisation et indicateurs, sans base ni horloge")
class AnalyseRapportsTest {

    @Test
    @DisplayName("chaque périodicité borne la période qui contient la référence ; la semaine commence le lundi")
    void bornes() {
        assertThat(Periodicites.bornesDe(Periodicite.QUOTIDIEN, "2027-03-14")).isEqualTo(new Bornes("2027-03-14", "2027-03-14"));
        assertThat(Periodicites.bornesDe(Periodicite.HEBDOMADAIRE, "2027-03-14")).isEqualTo(new Bornes("2027-03-08", "2027-03-14"));
        assertThat(Periodicites.bornesDe(Periodicite.MENSUEL, "2028-02-10")).isEqualTo(new Bornes("2028-02-01", "2028-02-29"));
        assertThat(Periodicites.bornesDe(Periodicite.TRIMESTRIEL, "2027-11-30")).isEqualTo(new Bornes("2027-10-01", "2027-12-31"));
        assertThat(Periodicites.bornesDe(Periodicite.ANNUEL, "2027-06-01")).isEqualTo(new Bornes("2027-01-01", "2027-12-31"));
        assertThatThrownBy(() -> Periodicites.bornesDe(Periodicite.PERSONNALISE, "2027-06-01")).hasMessageContaining("ses propres bornes");
        assertThatThrownBy(() -> Periodicites.bornesDe(Periodicite.MENSUEL, "2027-02-31")).hasMessage("la date de référence attend une date AAAA-MM-JJ");
        assertThatThrownBy(() -> Periodicites.bornesDe(Periodicite.MENSUEL, null)).hasMessage("la date de référence attend une date AAAA-MM-JJ");
        assertThatThrownBy(() -> Periodicites.bornesPersonnalisees("2027-03-02", "2027-03-01")).hasMessage("la fin de la période précède son début");
        assertThat(Periodicite.de(" annuel ")).isEqualTo(Periodicite.ANNUEL);
        assertThatThrownBy(() -> Periodicite.de(null)).hasMessageContaining("périodicité inconnue «  »");
    }

    @Test
    @DisplayName("la précédente suit le calendrier ; l'an passé ramène le 29 février au 28")
    void anterieures() {
        assertThat(Periodicites.precedente(Periodicite.MENSUEL, new Bornes("2027-03-01", "2027-03-31"))).isEqualTo(new Bornes("2027-02-01", "2027-02-28"));
        assertThat(Periodicites.precedente(Periodicite.TRIMESTRIEL, new Bornes("2027-01-01", "2027-03-31"))).isEqualTo(new Bornes("2026-10-01", "2026-12-31"));
        assertThat(Periodicites.precedente(Periodicite.PERSONNALISE, new Bornes("2027-03-10", "2027-03-12"))).isEqualTo(new Bornes("2027-03-07", "2027-03-09"));
        assertThat(Periodicites.memePeriodeLAnPasse(Periodicite.PERSONNALISE, new Bornes("2028-02-29", "2028-03-01")))
                .isEqualTo(new Bornes("2027-02-28", "2027-03-01"));
        assertThat(Periodicites.memePeriodeLAnPasse(Periodicite.HEBDOMADAIRE, new Bornes("2028-02-28", "2028-03-05")))
                .isEqualTo(new Bornes("2027-02-22", "2027-02-28"));
        assertThat(Periodicites.anterieures(Periodicite.QUOTIDIEN, new Bornes("2027-03-01", "2027-03-01"), Periodicites.Contre.PRECEDENTE))
                .isEqualTo(new Bornes("2027-02-28", "2027-02-28"));
    }

    @Test
    @DisplayName("comparer : une clé absente d'un côté apparaît, son écart est nul ; un précédent à zéro rend un pourcentage nul")
    void comparer() {
        Map<String, Object> vide = new HashMap<>();
        vide.put("unite", "c");
        vide.put("n", null);
        List<Map<String, Object>> courant = List.of(Map.of("unite", "a", "n", 10), Map.of("unite", "b", "n", "5"), vide, Map.of("unite", "d", "n", "abc"));
        List<Map<String, Object>> precedent = List.of(Map.of("unite", "a", "n", 0), Map.of("unite", "e", "n", 2.5));
        List<Periodicites.LigneComparee> l = Periodicites.comparer(courant, precedent, List.of("unite"), List.of("n"));
        assertThat(l).hasSize(5);
        assertThat(l.get(0).ecart()).containsEntry("n", 10.0);
        assertThat(l.get(0).ecartPourcent().get("n")).isNull();
        assertThat(l.get(1).courant()).containsEntry("n", 5.0);
        assertThat(l.get(1).precedent().get("n")).isNull();
        assertThat(l.get(2).courant().get("n")).isNull();
        assertThat(l.get(3).courant().get("n")).isNull();
        assertThat(l.get(4).cles()).containsEntry("unite", "e");
        assertThat(l.get(4).courant().get("n")).isNull();
    }

    private static Utilisation.CompteObserve compte(boolean actif, boolean invite, String activite) {
        return new Utilisation.CompteObserve("x", "employee", actif, invite, activite);
    }

    @Test
    @DisplayName("l'état d'un compte suit l'ordre invité, fermé, jamais actif, ancienneté ; le seuil se valide")
    void utilisation() {
        String maintenant = "2027-04-01T00:00:00Z";
        assertThat(Utilisation.etatDuCompte(compte(false, true, null), maintenant, 90)).isEqualTo(Utilisation.EtatCompte.INVITE);
        assertThat(Utilisation.etatDuCompte(compte(false, false, "2027-03-31T00:00:00Z"), maintenant, 90)).isEqualTo(Utilisation.EtatCompte.INACTIF);
        assertThat(Utilisation.etatDuCompte(compte(true, false, null), maintenant, 90)).isEqualTo(Utilisation.EtatCompte.SANS_ACTIVITE_RECENTE);
        assertThat(Utilisation.etatDuCompte(compte(true, false, "2027-01-01T00:00:00Z"), maintenant, 90)).isEqualTo(Utilisation.EtatCompte.ACTIF);
        assertThat(Utilisation.etatDuCompte(compte(true, false, "2026-12-31T23:00:00Z"), maintenant, 90)).isEqualTo(Utilisation.EtatCompte.SANS_ACTIVITE_RECENTE);
        assertThat(Utilisation.validerSeuil(null)).isEqualTo(90);
        assertThat(Utilisation.validerSeuil("")).isEqualTo(90);
        assertThat(Utilisation.validerSeuil("730")).isEqualTo(730);
        for (Object faux : List.of("abc", "1.5", "731", "0")) {
            assertThatThrownBy(() -> Utilisation.validerSeuil(faux)).hasMessageContaining("entre 1 et 730");
        }
        assertThat(Utilisation.usageDesQuarts(4, 6).aCombler()).isZero();
    }

    private static Map<String, Object> indicateur(Object... cles) {
        Map<String, Object> m = new LinkedHashMap<>(Map.of("code", "i", "source", "effectifs_journees", "format", "taux",
                "numerateur", "heuresDeclarees", "denominateur", "heuresPlanifiees"));
        for (int i = 0; i < cles.length; i += 2) {
            m.put(String.valueOf(cles[i]), cles[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("un indicateur mal défini est refusé nommément, champ par champ")
    void validation() {
        Map<Map<String, Object>, String> refus = new LinkedHashMap<>();
        refus.put(indicateur("code", " "), "le code d'un indicateur est obligatoire");
        refus.put(indicateur("code", "x".repeat(81)), "le code dépasse 80 caractères");
        refus.put(indicateur("format", "pourcent"), "format d'indicateur inconnu « pourcent »");
        refus.put(indicateur("direction", "haut"), "sens du seuil inconnu « haut »");
        refus.put(indicateur("numerateur", "nom"), "le numérateur « nom » n'est pas une mesure");
        refus.put(indicateur("denominateur", null), "un taux exige son dénominateur");
        refus.put(indicateur("denominateur", "nom"), "le dénominateur « nom » n'est pas une mesure");
        refus.put(indicateur("denominateur", "heuresDeclarees"), "rapporté à lui-même");
        refus.put(indicateur("axe", "journees"), "l'axe « journees » n'est pas un découpage");
        refus.put(indicateur("seuil", "beaucoup"), "le seuil doit être un nombre");
        refus.put(indicateur("seuil", -1), "le seuil d'un taux va de 0 à 100");
        refus.put(indicateur("marge", -1), "la marge de vigilance doit être un nombre positif");
        refus.put(indicateur("filtres", "unite"), "les filtres doivent être une liste");
        refus.put(indicateur("filtres", List.of("rien")), "le filtre porte sur «  »");
        refus.put(indicateur("filtres", List.of(Map.of("champ", "unite", "operateur", "ressemble"))), "opérateur de filtre inconnu « ressemble »");
        refus.put(indicateur("filtres", List.of(Map.of("champ", "unite", "operateur", "egal"))), "le filtre sur « unite » n'a aucune valeur");
        refus.put(indicateur("filtres", List.of(Map.of("champ", "jour", "operateur", "entre", "valeurs", List.of("2027-01-01")))), "exige deux bornes");
        refus.forEach((saisie, message) -> assertThatThrownBy(() -> Indicateurs.valider(saisie)).isInstanceOf(Indicateurs.IndicateurInvalideException.class)
                .hasMessageContaining(message));
        Indicateurs.Definition d = Indicateurs.valider(indicateur("format", "heures", "denominateur", "", "seuil", "", "marge", "2"));
        assertThat(d.denominateur()).isNull();
        assertThat(d.seuil()).isNull();
        assertThat(d.marge()).isEqualTo(2.0);
        assertThat(Indicateurs.valider(indicateur("seuil", 250, "format", "nombre")).seuil()).isEqualTo(250.0);
    }

    @Test
    @DisplayName("l'état suit la direction et la marge ; les filtres numériques comparent des nombres")
    void evaluation() {
        Indicateurs.Definition auPlus = Indicateurs.valider(indicateur("format", "nombre", "denominateur", null, "seuil", 10, "direction", "au_plus",
                "marge", 2, "filtres", List.of(Map.of("champ", "journees", "operateur", "superieur", "valeurs", List.of(9)))));
        assertThat(Indicateurs.etatDe(auPlus, 9.0)).isEqualTo(Indicateurs.Etat.ATTEINT);
        assertThat(Indicateurs.etatDe(auPlus, 12.0)).isEqualTo(Indicateurs.Etat.VIGILANCE);
        assertThat(Indicateurs.etatDe(auPlus, 13.0)).isEqualTo(Indicateurs.Etat.MANQUE);
        List<Map<String, Object>> faits = List.of(Map.of("journees", 10, "heuresDeclarees", 6), Map.of("journees", 2, "heuresDeclarees", 100),
                Map.of("journees", 11, "heuresDeclarees", "n/a"));
        List<Indicateurs.Valeur> v = Indicateurs.evaluer(auPlus, faits);
        assertThat(v).singleElement().satisfies(x -> {
            assertThat(x.valeur()).isEqualTo(6.0);
            assertThat(x.faits()).isEqualTo(2);
            assertThat(x.etat()).isEqualTo("atteint");
        });
        Indicateurs.Definition parAxe = Indicateurs.valider(indicateur("axe", "unite"));
        Map<String, Object> sansUnite = new HashMap<>(Map.of("heuresDeclarees", 1, "heuresPlanifiees", 2));
        assertThat(Indicateurs.evaluer(parAxe, List.of(sansUnite))).singleElement().satisfies(x -> {
            assertThat(x.decoupage()).isEmpty();
            assertThat(x.valeur()).isEqualTo(50.0);
            assertThat(x.etat()).isEqualTo("indetermine");
        });
    }
}

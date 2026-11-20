package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.Comportements.TypeHeure;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.Anomalie;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.CodeAnomalie;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.Issue;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.LignePaie;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.Parametres;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.QuartPlanifie;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.SegmentTemps;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.Source;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.StatutFeuille;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * <strong>Feuille de temps (lot R9) — le domaine pur</strong>, jumeau de {@code effectifs-temps-domaine.test.ts} : statuts et
 * paliers, paramètres, segments, heures supplémentaires sans double compte, écarts au planifié, anomalies, lot de paie.
 */
@DisplayName("TempsEffectifs — le pur de la feuille de temps")
class TempsEffectifsDomaineTest {

    private static final Parametres P = parametres(8, 40, 1, 15);

    private static Parametres parametres(int jour, int semaine, int paliers, int tolerance) {
        return new Parametres(jour, semaine, paliers, tolerance, 5, ActionCorroboration.SIGNALER, ActionCorroboration.BLOQUER, 0);
    }
    private static final QuartPlanifie QUART = new QuartPlanifie("pl-1", "2031-01-15", null, "2031-01-15T11:00:00.000Z", "2031-01-15T19:00:00.000Z");

    private static SegmentTemps seg(String date, String debut, String fin) {
        return seg(date, debut, fin, "REG", TypeHeure.TRAVAILLEE, Source.SAISIE, null);
    }

    private static SegmentTemps seg(String date, String debut, String fin, String type, TypeHeure c, Source source, String placeId) {
        return new SegmentTemps(date + "-" + debut, date, date + "T" + debut + ":00.000Z", date + "T" + fin + ":00.000Z", type, c, source, placeId);
    }

    private static Map<String, Object> carte(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("valide le statut et dit quand une feuille se corrige")
    void statuts() {
        assertThat(TempsEffectifs.statutDe("SOUMISE")).isEqualTo(StatutFeuille.SOUMISE);
        assertThatThrownBy(() -> TempsEffectifs.statutDe("PERDUE")).hasMessage("« statut » : BROUILLON, SOUMISE, RETOURNEE, APPROUVEE, PAYEE");
        assertThat(List.of(StatutFeuille.values()).stream().map(StatutFeuille::estModifiable)).containsExactly(true, false, true, false, false);
        assertThatCode(() -> TempsEffectifs.verifierModifiable(StatutFeuille.BROUILLON)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TempsEffectifs.verifierModifiable(StatutFeuille.APPROUVEE)).hasMessage("feuille approuvée : rouvrez-la ou rappelez-la avant la paie pour la corriger");
        assertThatThrownBy(() -> TempsEffectifs.verifierModifiable(StatutFeuille.SOUMISE))
                .hasMessage("feuille soumise : elle attend une décision ; rappelez-la ou faites-la retourner pour la corriger");
        assertThatCode(() -> TempsEffectifs.verifierSoumission(StatutFeuille.RETOURNEE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TempsEffectifs.verifierSoumission(StatutFeuille.APPROUVEE)).hasMessage("une feuille approuvée ne se soumet pas");
        assertThatThrownBy(() -> TempsEffectifs.verifierSoumission(StatutFeuille.SOUMISE)).hasMessage("une feuille déjà soumise ne se soumet pas");
        assertThatThrownBy(() -> TempsEffectifs.verifierSoumission(StatutFeuille.PAYEE)).hasMessage(TempsEffectifs.FEUILLE_PAYEE);
        assertThatThrownBy(() -> TempsEffectifs.verifierModifiable(StatutFeuille.PAYEE)).hasMessage(TempsEffectifs.FEUILLE_PAYEE);
    }

    @Test
    @DisplayName("avant la paie, une feuille se rappelle ou se rouvre ; payée, plus jamais ; seule une feuille approuvée se paie")
    void rappelReouvertureEtPaie() {
        assertThat(List.of(TempsEffectifs.apresRappel(StatutFeuille.SOUMISE), TempsEffectifs.apresRappel(StatutFeuille.APPROUVEE)))
                .containsOnly(new TempsEffectifs.Issue(StatutFeuille.BROUILLON, TempsEffectifs.AUCUN_PALIER));
        for (StatutFeuille s : List.of(StatutFeuille.BROUILLON, StatutFeuille.RETOURNEE)) {
            assertThatThrownBy(() -> TempsEffectifs.apresRappel(s)).hasMessage("une feuille qui n'est pas soumise n'a rien à rappeler");
        }
        assertThatThrownBy(() -> TempsEffectifs.apresRappel(StatutFeuille.PAYEE)).hasMessage(TempsEffectifs.FEUILLE_PAYEE);
        assertThat(TempsEffectifs.apresReouverture(StatutFeuille.APPROUVEE)).isEqualTo(new TempsEffectifs.Issue(StatutFeuille.RETOURNEE, TempsEffectifs.AUCUN_PALIER));
        assertThatThrownBy(() -> TempsEffectifs.apresReouverture(StatutFeuille.PAYEE)).hasMessage(TempsEffectifs.FEUILLE_PAYEE);
        for (StatutFeuille s : List.of(StatutFeuille.BROUILLON, StatutFeuille.SOUMISE, StatutFeuille.RETOURNEE)) {
            assertThatThrownBy(() -> TempsEffectifs.apresReouverture(s)).hasMessage("seule une feuille approuvée se rouvre ; une feuille soumise se retourne");
        }
        assertThat(TempsEffectifs.apresPaie(StatutFeuille.APPROUVEE)).isEqualTo(new TempsEffectifs.Issue(StatutFeuille.PAYEE, TempsEffectifs.AUCUN_PALIER));
        assertThatThrownBy(() -> TempsEffectifs.apresPaie(StatutFeuille.PAYEE)).hasMessage("feuille déjà payée");
        assertThatThrownBy(() -> TempsEffectifs.apresPaie(StatutFeuille.SOUMISE)).hasMessage("seule une feuille approuvée entre dans une paie");
        assertThat(List.of(TempsEffectifs.Decision.RAPPELER.actionJournal(), TempsEffectifs.Decision.ROUVRIR.actionJournal())).containsExactly("temps.rappeler", "temps.rouvrir");
    }

    @Test
    @DisplayName("approuve palier par palier, retourne une feuille soumise seulement")
    void paliers() {
        assertThat(TempsEffectifs.apresApprobation(StatutFeuille.SOUMISE, 1, 2)).isEqualTo(new Issue(StatutFeuille.SOUMISE, 2));
        assertThat(TempsEffectifs.apresApprobation(StatutFeuille.SOUMISE, 2, 2)).isEqualTo(new Issue(StatutFeuille.APPROUVEE, 2));
        assertThatThrownBy(() -> TempsEffectifs.apresApprobation(StatutFeuille.BROUILLON, 0, 1)).hasMessage("seule une feuille soumise s'approuve");
        assertThat(TempsEffectifs.apresRetour(StatutFeuille.SOUMISE)).isEqualTo(new Issue(StatutFeuille.RETOURNEE, 0));
        assertThatThrownBy(() -> TempsEffectifs.apresRetour(StatutFeuille.APPROUVEE)).hasMessage("seule une feuille soumise se retourne");
        assertThat(TempsEffectifs.Decision.RETOURNER.actionJournal()).isEqualTo("temps.retourner");
    }

    @Test
    @DisplayName("part des valeurs courantes, borne chaque seuil, lit les nombres comme le Node")
    void parametres() {
        assertThat(TempsEffectifs.parametresDe(Map.of(), TempsEffectifs.PARAMETRES_DEFAUT)).isEqualTo(P);
        assertThat(TempsEffectifs.parametresDe(Map.of("paliersApprobation", 2), parametres(10, 40, 1, 15))).isEqualTo(parametres(10, 40, 2, 15));
        assertThat(TempsEffectifs.parametresDe(Map.of("seuilJourHeures", "9", "seuilSemaineHeures", 44.0, "toleranceEcartMinutes", " "), P))
                .isEqualTo(parametres(9, 44, 1, 0));
        assertThat(TempsEffectifs.parametresDe(carte("toleranceEcartMinutes", null, "paliersApprobation", true), P)).isEqualTo(parametres(8, 40, 1, 0));
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("paliersApprobation", 4), P)).hasMessage("« paliersApprobation » : entier de 1 à 3");
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("seuilJourHeures", 7.5), P)).hasMessage("« seuilJourHeures » : entier de 1 à 24");
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("toleranceEcartMinutes", -1), P)).hasMessage("« toleranceEcartMinutes » : entier de 0 à 240");
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("seuilSemaineHeures", "abc"), P)).hasMessageContaining("seuilSemaineHeures");
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("seuilSemaineHeures", List.of()), P)).hasMessageContaining("seuilSemaineHeures");
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("seuilJourHeures", false), P)).hasMessageContaining("seuilJourHeures");
        assertThat(TempsEffectifs.Parametre.PALIERS.code()).isEqualTo("paliersApprobation");
    }

    @Test
    @DisplayName("la corroboration se paramètre : tolérance bornée, sort d'un ensemble fermé (E-RF-13)")
    void parametresDeCorroboration() {
        assertThat(List.of(P.toleranceCorroborationMinutes(), P.actionNonCorrobore(), P.actionSourcesDivergentes(), P.toleranceCorroborationMs()))
                .containsExactly(5, ActionCorroboration.SIGNALER, ActionCorroboration.BLOQUER, 300_000L);
        assertThat(TempsEffectifs.parametresDe(Map.of("toleranceCorroborationMinutes", 0, "actionNonCorrobore", "BLOQUER", "actionSourcesDivergentes", "IGNORER"), P))
                .isEqualTo(new Parametres(8, 40, 1, 15, 0, ActionCorroboration.BLOQUER, ActionCorroboration.IGNORER, 0));
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("toleranceCorroborationMinutes", 61), P))
                .hasMessage("« toleranceCorroborationMinutes » : entier de 0 à 60");
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("actionSourcesDivergentes", "bloquer"), P))
                .hasMessage("« actionSourcesDivergentes » : IGNORER, SIGNALER, BLOQUER");
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(carte("actionNonCorrobore", null), P)).hasMessage("« actionNonCorrobore » : IGNORER, SIGNALER, BLOQUER");
        // Les nombres d'abord, comme le Node : une borne fautive se dit avant un sort inconnu.
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("actionNonCorrobore", "PEUT-ETRE", "seuilJourHeures", 0), P)).hasMessageContaining("seuilJourHeures");
        assertThat(TempsEffectifs.ChoixCorroboration.SOURCES_DIVERGENTES.code()).isEqualTo("actionSourcesDivergentes");
        assertThat(List.of(ActionCorroboration.values()).stream().map(ActionCorroboration::bloque)).containsExactly(false, false, true);
    }

    @Test
    @DisplayName("un temps tiré d'une source externe se distingue d'un temps déduit de l'horaire (E-RF-13)")
    void sansPointageExterne() {
        List<Anomalie> a = TempsEffectifs.anomaliesDeBase(List.of(seg("2031-03-10", "08:00", "16:00", "REG", TypeHeure.TRAVAILLEE, Source.EXTERNE, null),
                seg("2031-03-11", "08:00", "16:00", "REG", TypeHeure.TRAVAILLEE, Source.GENERE, null)), List.of(), 15);
        assertThat(a).extracting(Anomalie::date, Anomalie::message).containsExactly(
                org.assertj.core.groups.Tuple.tuple("2031-03-11", "le 2031-03-11 n'a pas été pointé : le temps vient de l'horaire"),
                org.assertj.core.groups.Tuple.tuple("2031-03-10", "le 2031-03-10 n'a pas été pointé : le temps vient d'un horodatage externe, qui en est la seule preuve"));
    }

    @Test
    @DisplayName("lit un segment saisi et en refuse les défauts")
    void segmentSaisi() {
        Map<String, Object> base = carte("date", "2031-01-15", "debut", "2031-01-15T11:00:00Z", "fin", "2031-01-15T19:00:00Z", "typeHeureCode", " REG ");
        assertThat(TempsEffectifs.segmentSaisi(base))
                .isEqualTo(new TempsEffectifs.SegmentSaisi("2031-01-15", "2031-01-15T11:00:00.000Z", "2031-01-15T19:00:00.000Z", "REG"));
        assertThatThrownBy(() -> TempsEffectifs.segmentSaisi(avec(base, "date", "15/01/2031"))).hasMessage("« date » : AAAA-MM-JJ");
        assertThatThrownBy(() -> TempsEffectifs.segmentSaisi(avec(base, "date", 3))).hasMessage("« date » : AAAA-MM-JJ");
        assertThatThrownBy(() -> TempsEffectifs.segmentSaisi(avec(base, "debut", "hier"))).hasMessage("« debut » : instant ISO 8601");
        assertThatThrownBy(() -> TempsEffectifs.segmentSaisi(avec(base, "fin", 5))).hasMessage("« fin » : instant ISO 8601");
        assertThatThrownBy(() -> TempsEffectifs.segmentSaisi(avec(base, "fin", base.get("debut")))).hasMessage("la fin doit suivre le début");
        assertThatThrownBy(() -> TempsEffectifs.segmentSaisi(avec(base, "fin", "2031-01-16T11:00:01Z"))).hasMessage("un segment couvre 24 heures au plus");
        assertThatThrownBy(() -> TempsEffectifs.segmentSaisi(avec(base, "typeHeureCode", " "))).hasMessage("« typeHeureCode » : le type d'heure du segment");
        assertThatThrownBy(() -> TempsEffectifs.segmentSaisi(avec(base, "typeHeureCode", 4))).hasMessage("« typeHeureCode » : le type d'heure du segment");
    }

    private static Map<String, Object> avec(Map<String, Object> base, String cle, Object v) {
        Map<String, Object> m = new HashMap<>(base);
        m.put(cle, v);
        return m;
    }

    @Test
    @DisplayName("exige un motif quand la correction touche le temps d'autrui ou un temps prouvé")
    void motifs() {
        assertThat(TempsEffectifs.motifDeCorrection(" oubli ", false)).isEqualTo("oubli");
        assertThat(TempsEffectifs.motifDeCorrection(null, false)).isNull();
        assertThat(TempsEffectifs.motifDeCorrection("Borne en panne", true)).isEqualTo("Borne en panne");
        assertThatThrownBy(() -> TempsEffectifs.motifDeCorrection("ok", true))
                .hasMessage("« motif » : une correction du temps d'autrui ou d'un temps pointé se motive");
        assertThatThrownBy(() -> TempsEffectifs.motifDeCorrection(7, true)).hasMessageContaining("« motif »");
        assertThat(TempsEffectifs.texteLibre("x".repeat(600))).hasSize(500);
    }

    @Test
    @DisplayName("totalise par type, distingués (E-RF-6) ; la pause non payée n'est pas payée ; date locale du Québec")
    void totaux() {
        List<SegmentTemps> s = List.of(seg("2031-01-15", "11:00", "15:00"), seg("2031-01-15", "15:00", "15:30", "PNP", TypeHeure.PAUSE_NON_PAYEE, Source.SAISIE, null),
                seg("2031-01-15", "15:30", "19:00"));
        assertThat(TempsEffectifs.totauxParType(s)).containsExactly(entry("REG", 450L), entry("PNP", 30L));
        assertThat(List.of(TempsEffectifs.estPaye(TypeHeure.PAUSE_NON_PAYEE), TempsEffectifs.estPaye(TypeHeure.ABSENCE_PAYEE))).containsExactly(false, true);
        assertThat(TempsEffectifs.minutesDe("2031-01-15T11:00:00Z", "2031-01-15T11:45:00Z")).isEqualTo(45);
        assertThat(TempsEffectifs.dateLocaleDe("2031-01-16T03:00:00.000Z")).isEqualTo("2031-01-15");
        assertThat(TempsEffectifs.dateLocaleDe("2031-01-16T03:00:00.000Z", "UTC")).isEqualTo("2031-01-16");
        assertThat(List.of(PresenceEffectifs.TypeSegment.values()).stream().map(TempsEffectifs::comportementDu))
                .containsExactly(TypeHeure.PRESENCE_PREALABLE, TypeHeure.TRAVAILLEE, TypeHeure.PAUSE_NON_PAYEE);
    }

    @Test
    @DisplayName("compte l'excédent du jour, puis celui de la semaine sur le reste, sans double compte")
    void heuresSupplementaires() {
        List<SegmentTemps> semaine = new ArrayList<>(List.of(seg("2031-01-13", "08:00", "18:00")));
        for (String d : List.of("14", "15", "16", "17")) {
            semaine.add(seg("2031-01-" + d, "08:00", "17:00"));
        }
        TempsEffectifs.HeuresSupplementaires hs = TempsEffectifs.heuresSupplementaires(semaine, P);
        assertThat(hs.parJour()).containsExactly(entry("2031-01-13", 120L), entry("2031-01-14", 60L), entry("2031-01-15", 60L), entry("2031-01-16", 60L),
                entry("2031-01-17", 60L));
        assertThat(hs.total()).isEqualTo(360);
        List<SegmentTemps> six = List.of("13", "14", "15", "16", "17", "18").stream().map(d -> seg("2031-01-" + d, "08:00", "16:00")).toList();
        assertThat(TempsEffectifs.heuresSupplementaires(six, P)).isEqualTo(new TempsEffectifs.HeuresSupplementaires(Map.of("2031-01-18", 480L), 480));
        assertThat(TempsEffectifs.heuresSupplementaires(six, parametres(8, 36, 1, 15)).parJour())
                .containsOnly(entry("2031-01-17", 240L), entry("2031-01-18", 480L));
    }

    @Test
    @DisplayName("AD-03 — le seuil de la période de paie compte sur le reste, après le jour et la semaine ; 0 = aucun")
    void periodeDePaie() {
        // Deux semaines de 5 × 8 h = 80 h : aucun excédent de jour (8 h) ni de semaine (40 h).
        List<SegmentTemps> deuxSemaines = new ArrayList<>();
        for (String d : List.of("13", "14", "15", "16", "17", "20", "21", "22", "23", "24")) {
            deuxSemaines.add(seg("2031-01-" + d, "08:00", "16:00"));
        }
        assertThat(TempsEffectifs.heuresSupplementaires(deuxSemaines, P).total()).isZero();
        Parametres soixanteDouze = TempsEffectifs.parametresDe(Map.of("seuilPeriodePaieHeures", 72), P);
        // 80 h pour 72 : 8 h d'excédent, posées sur le dernier jour.
        assertThat(TempsEffectifs.heuresSupplementaires(deuxSemaines, soixanteDouze)).isEqualTo(new TempsEffectifs.HeuresSupplementaires(Map.of("2031-01-24", 480L), 480));
        // Une semaine à 45 h : l'excédent de semaine (5 h) n'est pas recompté par la période (85 h - 5 h = 80 h, seuil 80 h).
        List<SegmentTemps> lourde = new ArrayList<>(deuxSemaines);
        lourde.add(seg("2031-01-18", "08:00", "13:00"));
        Parametres quatreVingts = TempsEffectifs.parametresDe(Map.of("seuilPeriodePaieHeures", 80), P);
        assertThat(TempsEffectifs.heuresSupplementaires(lourde, quatreVingts)).isEqualTo(new TempsEffectifs.HeuresSupplementaires(Map.of("2031-01-18", 300L), 300));
        // L'excédent de période se répartit du dernier jour vers le premier quand il dépasse une journée.
        assertThat(TempsEffectifs.heuresSupplementaires(deuxSemaines, TempsEffectifs.parametresDe(Map.of("seuilPeriodePaieHeures", 70), P)).parJour())
                .containsOnly(entry("2031-01-23", 120L), entry("2031-01-24", 480L));
        assertThatThrownBy(() -> TempsEffectifs.parametresDe(Map.of("seuilPeriodePaieHeures", 745), P)).hasMessage("« seuilPeriodePaieHeures » : entier de 0 à 744");
    }

    @Test
    @DisplayName("AD-03 — une convention précise les seuils de l'organisme ; un seuil vide hérite ; tout vide retire")
    void seuilsDeConvention() {
        Parametres p = P.selon(SeuilsConvention.de("SPCSN", carte("seuilJourHeures", null, "seuilPeriodePaieHeures", 80)));
        assertThat(List.of(p.seuilJourHeures(), p.seuilSemaineHeures(), p.seuilPeriodePaieHeures(), p.paliersApprobation())).containsExactly(8, 40, 80, 1);
        Parametres q = P.selon(new SeuilsConvention("X", 9, 38, null));
        assertThat(List.of(q.seuilJourHeures(), q.seuilSemaineHeures(), q.seuilPeriodePaieHeures())).containsExactly(9, 38, 0);
        assertThat(SeuilsConvention.de("SPCSN", Map.of()).estVide()).isTrue();
        assertThat(List.of(new SeuilsConvention("X", 9, null, null).estVide(), new SeuilsConvention("X", null, 1, null).estVide(),
                new SeuilsConvention("X", null, null, 1).estVide())).containsExactly(false, false, false);
        assertThatThrownBy(() -> SeuilsConvention.de("X", Map.of("seuilJourHeures", 25))).hasMessage("« seuilJourHeures » : entier de 1 à 24, ou vide pour celui de l'organisme");
        assertThatThrownBy(() -> SeuilsConvention.de("X", Map.of("seuilSemaineHeures", 0))).hasMessageContaining("de 1 à 168");
        assertThatThrownBy(() -> SeuilsConvention.de("X", Map.of("seuilPeriodePaieHeures", 7.5))).hasMessageContaining("de 1 à 744");
        assertThatThrownBy(() -> SeuilsConvention.de("X", Map.of("seuilPeriodePaieHeures", "80"))).hasMessageContaining("seuilPeriodePaieHeures");
    }

    @Test
    @DisplayName("les heures supplémentaires déclarées comptent sous le seuil, et ne se paient jamais deux fois au-dessus")
    void supplementairesDeclarees() {
        TempsEffectifs.CodesPaie codes = new TempsEffectifs.CodesPaie(Map.of("REG", "100", "HS", "150"), "100", "150");
        // 6 h + 2 h déclarées, seuil de 8 h : 2 h supplémentaires, rien à reclasser.
        List<SegmentTemps> sous = List.of(seg("2031-01-15", "08:00", "14:00"), seg("2031-01-15", "14:00", "16:00", "HS", TypeHeure.SUPPLEMENTAIRE, Source.SAISIE, null));
        TempsEffectifs.HeuresSupplementaires hs = TempsEffectifs.heuresSupplementaires(sous, P);
        assertThat(hs).isEqualTo(new TempsEffectifs.HeuresSupplementaires(Map.of("2031-01-15", 120L), 120));
        assertThat(TempsEffectifs.lignesDePaie("P1", sous, codes, hs.parJour()))
                .containsExactly(new LignePaie("P1", "2031-01-15", "100", 6L), new LignePaie("P1", "2031-01-15", "150", 2L));
        // 8 h + 2 h déclarées : l'excédent, ce sont les 2 h déclarées — 2 h majorées, et non 4.
        List<SegmentTemps> dessus = List.of(seg("2031-01-15", "08:00", "16:00"), seg("2031-01-15", "16:00", "18:00", "HS", TypeHeure.SUPPLEMENTAIRE, Source.SAISIE, null));
        hs = TempsEffectifs.heuresSupplementaires(dessus, P);
        assertThat(hs.total()).isEqualTo(120);
        assertThat(TempsEffectifs.lignesDePaie("P1", dessus, codes, hs.parJour()))
                .containsExactly(new LignePaie("P1", "2031-01-15", "100", 8L), new LignePaie("P1", "2031-01-15", "150", 2L));
        // 9 h + 2 h déclarées : 3 h d'excédent, dont 2 déclarées ; une seule heure régulière se reclasse.
        List<SegmentTemps> plus = List.of(seg("2031-01-15", "08:00", "17:00"), seg("2031-01-15", "17:00", "19:00", "HS", TypeHeure.SUPPLEMENTAIRE, Source.SAISIE, null));
        hs = TempsEffectifs.heuresSupplementaires(plus, P);
        assertThat(hs.total()).isEqualTo(180);
        assertThat(TempsEffectifs.lignesDePaie("P1", plus, codes, hs.parJour()))
                .containsExactly(new LignePaie("P1", "2031-01-15", "100", 8L), new LignePaie("P1", "2031-01-15", "150", 3L));
        assertThat(TempsEffectifs.supplementairesDeclarees(plus)).containsExactly(entry("2031-01-15", 120L));
    }

    @Test
    @DisplayName("ignore l'absence payée et la pause non payée ; semaines séparées")
    void semainesSeparees() {
        List<SegmentTemps> s = List.of(seg("2031-01-13", "08:00", "20:00", "ABS", TypeHeure.ABSENCE_PAYEE, Source.SAISIE, null),
                seg("2031-01-13", "20:00", "21:00", "PNP", TypeHeure.PAUSE_NON_PAYEE, Source.SAISIE, null), seg("2031-01-20", "08:00", "17:00"));
        assertThat(TempsEffectifs.heuresSupplementaires(s, P)).isEqualTo(new TempsEffectifs.HeuresSupplementaires(Map.of("2031-01-20", 60L), 60));
        assertThat(TempsEffectifs.heuresSupplementaires(List.of(), P)).isEqualTo(new TempsEffectifs.HeuresSupplementaires(Map.of(), 0));
    }

    @Test
    @DisplayName("compare le déclaré payé au planifié, quart par quart")
    void ecarts() {
        List<SegmentTemps> s = List.of(seg("2031-01-15", "11:00", "15:00", "REG", TypeHeure.TRAVAILLEE, Source.SAISIE, "pl-1"),
                seg("2031-01-15", "15:00", "15:30", "PNP", TypeHeure.PAUSE_NON_PAYEE, Source.SAISIE, "pl-1"),
                seg("2031-01-15", "15:30", "19:40", "REG", TypeHeure.TRAVAILLEE, Source.SAISIE, "pl-1"), seg("2031-01-15", "20:00", "21:00"));
        assertThat(TempsEffectifs.ecartsAuPlanifie(s, List.of(QUART))).containsExactly(new TempsEffectifs.EcartPlanifie("pl-1", "2031-01-15", null,
                QUART.debutPrevu(), QUART.finPrevue(), 480, 490, 10));
    }

    @Test
    @DisplayName("relève chevauchements, écarts hors tolérance et jours non pointés ; trie par date puis code")
    void anomalies() {
        List<SegmentTemps> s = List.of(seg("2031-01-15", "11:00", "16:00", "REG", TypeHeure.TRAVAILLEE, Source.GENERE, "pl-1"), seg("2031-01-15", "15:00", "17:00"));
        List<Anomalie> a = TempsEffectifs.anomaliesDeBase(s, List.of(QUART), 15);
        assertThat(a).extracting(Anomalie::code, Anomalie::bloquante, Anomalie::message).containsExactly(
                org.assertj.core.groups.Tuple.tuple(CodeAnomalie.CHEVAUCHEMENT, true, "deux segments se chevauchent le 2031-01-15"),
                org.assertj.core.groups.Tuple.tuple(CodeAnomalie.ECART_PLANIFIE, false, "déclaré 5 h 00 pour 8 h 00 planifiées (écart -3 h 00)"),
                org.assertj.core.groups.Tuple.tuple(CodeAnomalie.SANS_POINTAGE, false, "le 2031-01-15 n'a pas été pointé : le temps vient de l'horaire"));
        assertThat(TempsEffectifs.bloquantes(a)).extracting(Anomalie::code).containsExactly(CodeAnomalie.CHEVAUCHEMENT);
        assertThat(TempsEffectifs.anomaliesDeBase(List.of(seg("2031-01-15", "11:00", "19:10", "REG", TypeHeure.TRAVAILLEE, Source.SAISIE, "pl-1")),
                List.of(QUART), 15)).isEmpty();
        assertThat(TempsEffectifs.hm(75)).isEqualTo("1 h 15");
        Anomalie tard = new Anomalie(CodeAnomalie.REGLE, "2031-01-16", "r", false);
        Anomalie tot = new Anomalie(CodeAnomalie.SANS_POINTAGE, "2031-01-15", "s", false);
        Anomalie avant = new Anomalie(CodeAnomalie.ECART_PLANIFIE, "2031-01-15", "e", false);
        assertThat(TempsEffectifs.triees(List.of(tard, tot, avant))).containsExactly(avant, tot, tard);
    }

    @Test
    @DisplayName("expose aux règles les faits de la journée : heures du jour, de la semaine, écart, pointage, férié")
    void faits() {
        List<SegmentTemps> s = List.of(seg("2031-01-15", "11:00", "20:00", "REG", TypeHeure.TRAVAILLEE, Source.GENERE, "pl-1"), seg("2031-01-14", "11:00", "14:00"),
                seg("2031-01-20", "11:00", "14:00"));
        Map<ContexteRegle, Map<String, Object>> f = TempsEffectifs.faitsDeLaJournee(s, List.of(QUART), new TempsEffectifs.Journee("2031-01-15", true));
        assertThat(f.get(ContexteRegle.FEUILLE_DE_TEMPS)).containsExactly(entry("heuresJour", 9.0), entry("heuresSemaine", 12.0),
                entry("ecartPlanifieMinutes", 60L), entry("sansPointage", true));
        // La période de paie compte jusqu'au jour jugé : le 20 n'y entre pas encore.
        assertThat(f.get(ContexteRegle.HEURES_SUPPLEMENTAIRES)).containsExactly(entry("heuresJour", 9.0), entry("heuresSemaine", 12.0), entry("jourFerie", true),
                entry("heuresPeriodePaie", 12.0));
        Map<ContexteRegle, Map<String, Object>> vide = TempsEffectifs.faitsDeLaJournee(s, List.of(), new TempsEffectifs.Journee("2031-01-16", false));
        assertThat(vide.get(ContexteRegle.FEUILLE_DE_TEMPS)).containsEntry("heuresJour", 0.0).containsEntry("sansPointage", false).containsEntry("ecartPlanifieMinutes", 0L);
    }

    @Test
    @DisplayName("code les lignes de paie, reclasse les heures supplémentaires, et signe le lot")
    void lotDePaie() {
        List<SegmentTemps> s = List.of(seg("2031-01-15", "08:00", "18:00"), seg("2031-01-15", "18:00", "18:30", "PNP", TypeHeure.PAUSE_NON_PAYEE, Source.SAISIE, null),
                seg("2031-01-16", "08:00", "09:00", "PREA", TypeHeure.PRESENCE_PREALABLE, Source.SAISIE, null), seg("2031-01-17", "08:00", "08:50"));
        TempsEffectifs.CodesPaie codes = new TempsEffectifs.CodesPaie(Map.of("REG", "100", "PREA", "105"), "100", "150");
        List<LignePaie> lignes = TempsEffectifs.lignesDePaie("P1", s, codes, Map.of("2031-01-15", 120L, "2031-01-16", 30L));
        assertThat(lignes).containsExactly(new LignePaie("P1", "2031-01-15", "100", 8L), new LignePaie("P1", "2031-01-15", "150", 2L),
                new LignePaie("P1", "2031-01-16", "105", 1L), new LignePaie("P1", "2031-01-17", "100", 0.83));
        assertThat(TempsEffectifs.empreinteDe(lignes)).matches("^[0-9a-f]{64}$").isEqualTo(TempsEffectifs.empreinteDe(List.copyOf(lignes)))
                .isNotEqualTo(TempsEffectifs.empreinteDe(lignes.subList(1, lignes.size())));
        // L'empreinte du Node pour une ligne « P1;2031-01-15;100;7.83 » : SHA-256 de ce texte exact.
        assertThat(TempsEffectifs.empreinteDe(List.of(new LignePaie("P1", "2031-01-15", "100", 7.83))))
                .isEqualTo(sha256("P1;2031-01-15;100;7.83"));
        assertThat(TempsEffectifs.empreinteDe(List.of(new LignePaie("P1", "2031-01-16", "100", 8L), new LignePaie("P1", "2031-01-16", "150", 4L))))
                .isEqualTo(sha256("P1;2031-01-16;100;8.00\nP1;2031-01-16;150;4.00"));
    }

    private static String sha256(String texte) {
        return java.util.HexFormat.of().formatHex(ca.vina.nexu.partage.crypto.Primitives.sha256(texte.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("valide le regroupement des rapports")
    void regroupements() {
        assertThat(List.of(TempsEffectifs.regroupementDe(null), TempsEffectifs.regroupementDe(""), TempsEffectifs.regroupementDe("semaine")))
                .containsExactly(TempsEffectifs.Regroupement.UNITE, TempsEffectifs.Regroupement.UNITE, TempsEffectifs.Regroupement.SEMAINE);
        assertThatThrownBy(() -> TempsEffectifs.regroupementDe("mois")).hasMessage("« regroupement » : unite, ressource, typeHeure, semaine");
    }
}

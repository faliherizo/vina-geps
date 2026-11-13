package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Domaine du moteur de règles</strong> (AO 26-015-DO, lot R2) — jumeau de
 * {@code effectifs-regles-domaine.test.ts}, 100 % exigé. Les propriétés qui comptent : le particulier
 * remplace le général <strong>à clé égale</strong> et seulement là ; l'action la plus restrictive l'emporte
 * mais tous les motifs restent ; un fait manquant n'est jamais un « faux » silencieux ; la date du fait
 * choisit la version.
 */
@DisplayName("Effectifs — domaine du moteur de règles")
class ReglesEffectifsDomaineTest {

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            r.put((String) kv[i], kv[i + 1]);
        }
        return r;
    }

    private static ConditionRegle.Comparaison cmp(String fait, OperateurRegle op, Object valeur) {
        return new ConditionRegle.Comparaison(fait, op, valeur);
    }

    /** Une version de règle d'essai, modifiable champ par champ (le {@code v({...})} du Node). */
    private static final class V {
        private String regleId;
        private String cle = "minimum-quarts";
        private ContexteRegle contexte = ContexteRegle.DISPONIBILITE;
        private PorteeRegle portee = PorteeRegle.ORGANISME;
        private String code;
        private ConditionRegle condition = cmp("quartsParSemaine", OperateurRegle.INFERIEUR, 3);
        private ActionRegle action = ActionRegle.BLOQUER;
        private String message = "Au moins 3 quarts ({quartsParSemaine} déclarés)";
        private int priorite;
        private String jusqua;

        V cle(String c) {
            cle = c;
            return this;
        }

        V contexte(ContexteRegle c) {
            contexte = c;
            return this;
        }

        V portee(PorteeRegle p, String c) {
            portee = p;
            code = c;
            return this;
        }

        V condition(ConditionRegle c) {
            condition = c;
            return this;
        }

        V action(ActionRegle a) {
            action = a;
            return this;
        }

        V message(String x) {
            message = x;
            return this;
        }

        V priorite(int p) {
            priorite = p;
            return this;
        }

        V jusqua(String j) {
            jusqua = j;
            return this;
        }

        VersionRegle v() {
            return new VersionRegle("v-" + regleId, new Regle(regleId, contexte, cle, portee, code), 1, new ContenuRegle(condition, action, message, priorite),
                    "2027-01-01", jusqua);
        }
    }

    private static V v(String regleId) {
        V x = new V();
        x.regleId = regleId;
        return x;
    }

    private static List<String> ids(List<VersionRegle> versions) {
        return versions.stream().map(x -> x.regle().id()).toList();
    }

    private static PorteesDuDossier unites(String... u) {
        return PorteesDuDossier.de(Arrays.asList(u), null);
    }

    @Nested
    @DisplayName("conversions à la frontière")
    class Conversions {
        @Test
        void acceptentLesValeursConnuesEtNommentLAttendu() {
            assertThat(List.of(ContexteRegle.de("ABSENCE"), ActionRegle.de("AVERTIR"), PorteeRegle.de("UNITE"), OperateurRegle.de("dans")))
                    .containsExactly(ContexteRegle.ABSENCE, ActionRegle.AVERTIR, PorteeRegle.UNITE, OperateurRegle.DANS);
            assertThatThrownBy(() -> ContexteRegle.de("PAIE")).isInstanceOf(EffectifsInvalideException.class)
                    .hasMessage("contexte inconnu : « PAIE » (attendu : DISPONIBILITE, ABSENCE, ATTRIBUTION, ECHANGE, POINTAGE, FEUILLE_DE_TEMPS, "
                            + "HEURES_SUPPLEMENTAIRES, CHOIX_AGPH, INTERET_REMPLACEMENT, DEPOT_AGPH, MODIFICATION_HEURES)");
            assertThatThrownBy(() -> ActionRegle.de("IGNORER")).hasMessageContaining("action inconnu").hasMessageContaining("ACCEPTER");
            assertThatThrownBy(() -> PorteeRegle.de("PAYS")).hasMessageContaining("portée inconnu").hasMessageContaining("ORGANISME");
            assertThatThrownBy(() -> OperateurRegle.de("~")).hasMessage("opérateur inconnu : « ~ » (attendu : =, !=, <, <=, >, >=, dans, hors)");
            assertThatThrownBy(() -> OperateurRegle.de(null)).hasMessageContaining("« null »");
        }

        @Test
        void valideCleMessageEtPriorite() {
            assertThat(Regles.cleValide(" minimum-quarts ")).isEqualTo("minimum-quarts");
            assertThatThrownBy(() -> Regles.cleValide("Minimum Quarts")).hasMessage("« clé » : 2 à 60 minuscules, chiffres ou tirets (ex. minimum-quarts-semaine)");
            assertThatThrownBy(() -> Regles.cleValide(12)).hasMessageContaining("clé");
            assertThat(Regles.messageValide(" Corrigez ")).isEqualTo("Corrigez");
            assertThatThrownBy(() -> Regles.messageValide("")).hasMessage("« message » est obligatoire : il dit à la personne quoi corriger");
            assertThatThrownBy(() -> Regles.messageValide(Map.of("texte", "x"))).hasMessageContaining("quoi corriger");
            assertThatThrownBy(() -> Regles.messageValide("x".repeat(501))).hasMessage("« message » : 500 caractères au plus");
            assertThat(Regles.messageValide("x".repeat(500))).hasSize(500);
            assertThat(List.of(Regles.prioriteValide(null), Regles.prioriteValide(""), Regles.prioriteValide("10"), Regles.prioriteValide(" "),
                    Regles.prioriteValide(7), Regles.prioriteValide(3.0), Regles.prioriteValide(1000))).containsExactly(0, 0, 10, 0, 7, 3, 1000);
            for (Object faux : new Object[]{1.5, 2000, -1, "abc", Double.NaN, true}) {
                assertThatThrownBy(() -> Regles.prioriteValide(faux)).as(String.valueOf(faux)).hasMessage("« priorité » : entier entre 0 et 1000");
            }
        }

        @Test
        void leCatalogueDeclareDesFaitsPourChaqueContexteEtRefuseUnFaitInconnu() {
            for (ContexteRegle c : ContexteRegle.values()) {
                assertThat(c.faits()).as(c.code()).isNotEmpty();
            }
            assertThat(ContexteRegle.POINTAGE.fait("distanceMetres").type()).isEqualTo(TypeFaitRegle.NOMBRE);
            assertThatThrownBy(() -> ContexteRegle.POINTAGE.fait("vitesse"))
                    .hasMessage("fait inconnu pour POINTAGE : « vitesse » (attendu : ecartDebutMinutes, distanceMetres, mecanisme, preuveExigeePresente)");
        }

        @Test
        void typesDeFaitsEtOperateurs() {
            assertThat(TypeFaitRegle.NOMBRE.admet(3)).isTrue();
            assertThat(TypeFaitRegle.NOMBRE.admet(Double.POSITIVE_INFINITY)).isFalse();
            assertThat(TypeFaitRegle.NOMBRE.admet("3")).isFalse();
            assertThat(TypeFaitRegle.TEXTE.admet("a")).isTrue();
            assertThat(TypeFaitRegle.TEXTE.admet(1)).isFalse();
            assertThat(TypeFaitRegle.BOOLEEN.admet(true)).isTrue();
            assertThat(TypeFaitRegle.BOOLEEN.admet("oui")).isFalse();
            assertThat(TypeFaitRegle.DATE.admet("2027-01-01")).isTrue();
            assertThat(TypeFaitRegle.DATE.admet("demain")).isFalse();
            assertThat(TypeFaitRegle.DATE.admet(20270101)).isFalse();
            assertThat(Arrays.stream(TypeFaitRegle.values()).map(TypeFaitRegle::code)).containsExactly("nombre", "texte", "booleen", "date");
            assertThat(Arrays.stream(TypeFaitRegle.values()).filter(TypeFaitRegle::ordonnable)).containsExactly(TypeFaitRegle.NOMBRE, TypeFaitRegle.DATE);
            assertThat(Arrays.stream(OperateurRegle.values()).filter(OperateurRegle::ordonne)).hasSize(4);
            assertThat(Arrays.stream(OperateurRegle.values()).filter(OperateurRegle::ensembliste)).containsExactly(OperateurRegle.DANS, OperateurRegle.HORS);
        }

        @Test
        void porteesEtReferentiels() {
            assertThat(PorteeRegle.ORGANISME.referentiel()).isEmpty();
            assertThat(Arrays.stream(PorteeRegle.values()).skip(1).map(p -> p.referentiel().orElseThrow())).containsExactly(TypeReferentiel.UNITES,
                    TypeReferentiel.CONVENTIONS, TypeReferentiel.STATUTS_EMPLOI, TypeReferentiel.CORPS_EMPLOI, TypeReferentiel.TITRES_EMPLOI);
            PorteesDuDossier p = PorteesDuDossier.de(List.of("EST"), Map.of(PorteesDuDossier.CONVENTION, "FSSS", PorteesDuDossier.STATUT_EMPLOI, "TPR",
                    PorteesDuDossier.CORPS_EMPLOI, "PARA", PorteesDuDossier.TITRE_EMPLOI, "PSA"));
            assertThat(p).isEqualTo(new PorteesDuDossier(List.of("EST"), "FSSS", "TPR", "PARA", "PSA"));
            assertThat(PorteeRegle.CONVENTION.vise(null, new PorteesDuDossier(List.of(), null, null, null, null))).isFalse();
            assertThat(PorteeRegle.UNITE.vise(null, unites(""))).isTrue();
            assertThat(new Regle("a", ContexteRegle.ABSENCE, "k", PorteeRegle.ORGANISME, null).avecId("b").id()).isEqualTo("b");
        }
    }

    @Nested
    @DisplayName("validation d'une condition")
    class Validation {
        @Test
        void normaliseUneConditionImbriqueeValide() {
            ConditionRegle c = ConditionsRegles.valider(ContexteRegle.DISPONIBILITE, m("tous", List.of(
                    m("fait", "quartsParSemaine", "op", "<", "valeur", 3),
                    m("un_de", List.of(m("fait", "statutEmploi", "op", "dans", "valeur", List.of("TEMPS_PARTIEL_REGULIER")))),
                    m("non", m("fait", "corpsEmploi", "op", "=", "valeur", "RMU")))));
            assertThat(ConditionsRegles.faitsLus(c)).containsExactly("quartsParSemaine", "statutEmploi", "corpsEmploi");
            assertThat(c.enDonnees()).isEqualTo(Map.of("tous", List.of(
                    m("fait", "quartsParSemaine", "op", "<", "valeur", 3),
                    Map.of("un_de", List.of(m("fait", "statutEmploi", "op", "dans", "valeur", List.of("TEMPS_PARTIEL_REGULIER")))),
                    Map.of("non", m("fait", "corpsEmploi", "op", "=", "valeur", "RMU")))));
            assertThat(ConditionsRegles.valider(ContexteRegle.ECHANGE, m("fait", "contigu", "op", "=", "valeur", true)))
                    .isEqualTo(cmp("contigu", OperateurRegle.EGAL, true));
        }

        @Test
        void refuseLesFormesInvalides() {
            ContexteRegle d = ContexteRegle.DISPONIBILITE;
            assertThatThrownBy(() -> ConditionsRegles.valider(d, null)).hasMessage("une condition est un objet");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, List.of(1))).hasMessage("une condition est un objet");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("tous", List.of()))).hasMessage("« tous » attend une liste non vide");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("un_de", "x"))).hasMessage("« un_de » attend une liste non vide");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("fait", "statutEmploi", "op", "<", "valeur", "x")))
                    .hasMessage("l'opérateur « < » ne s'applique pas à « Comportement du statut d'emploi » (texte)");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("fait", "quartsParSemaine", "op", "=", "valeur", "3")))
                    .hasMessage("« Quarts disponibles dans la semaine » attend une valeur de type nombre");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("fait", "quartsParSemaine", "op", "dans", "valeur", List.of())))
                    .hasMessage("« Quarts disponibles dans la semaine » dans : une liste non vide de valeurs de type nombre est attendue");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("fait", "quartsParSemaine", "op", "hors", "valeur", List.of("3")))).hasMessageContaining("liste non vide");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("fait", "quartsParSemaine", "op", "hors", "valeur", 3))).hasMessageContaining("liste non vide");
            assertThatThrownBy(() -> ConditionsRegles.valider(ContexteRegle.ECHANGE, m("fait", "contigu", "op", "=", "valeur", "oui"))).hasMessageContaining("booleen");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("fait", "quartsParSemaine", "op", "="))).hasMessageContaining("type nombre");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("op", "=", "valeur", 1))).hasMessageContaining("fait inconnu pour DISPONIBILITE : «  »");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("fait", 12, "op", "=", "valeur", 1))).hasMessageContaining("« 12 »");
            assertThatThrownBy(() -> ConditionsRegles.valider(d, m("fait", "quartsParSemaine", "op", "~", "valeur", 1))).hasMessageContaining("opérateur inconnu");
        }

        @Test
        void accepteLesDatesAvecLesOperateursDOrdre() {
            assertThat(ConditionsRegles.valider(ContexteRegle.ABSENCE, m("fait", "premierJour", "op", ">=", "valeur", "2027-01-01")))
                    .isEqualTo(cmp("premierJour", OperateurRegle.SUPERIEUR_OU_EGAL, "2027-01-01"));
            assertThatThrownBy(() -> ConditionsRegles.valider(ContexteRegle.ABSENCE, m("fait", "premierJour", "op", ">=", "valeur", "demain")))
                    .hasMessageContaining("type date");
        }

        @Test
        void borneLaProfondeurEtLeNombreDeComparaisons() {
            Object profond = m("fait", "quartsParSemaine", "op", "<", "valeur", 1);
            for (int i = 0; i < 6; i++) {
                profond = m("tous", List.of(profond));
            }
            Object trop = profond;
            assertThatThrownBy(() -> ConditionsRegles.valider(ContexteRegle.DISPONIBILITE, trop)).hasMessage("condition trop imbriquée (5 niveaux au plus)");
            List<Object> feuilles = new ArrayList<>();
            for (int i = 0; i < 31; i++) {
                feuilles.add(m("fait", "quartsParSemaine", "op", "<", "valeur", 1));
            }
            assertThatThrownBy(() -> ConditionsRegles.valider(ContexteRegle.DISPONIBILITE, m("tous", feuilles))).hasMessage("condition trop longue (30 comparaisons au plus)");
            assertThat(ConditionsRegles.valider(ContexteRegle.DISPONIBILITE, m("tous", feuilles.subList(0, 30)))).isInstanceOf(ConditionRegle.Tous.class);
        }
    }

    @Nested
    @DisplayName("évaluation d'une condition")
    class Evaluation {
        private final Map<String, Object> f = m("n", 5, "t", "A", "b", true, "d", "2027-02-01");

        private boolean c(OperateurRegle op, Object valeur, String fait) {
            return ConditionsRegles.evaluer(cmp(fait, op, valeur), f);
        }

        @Test
        void compareSelonChaqueOperateur() {
            assertThat(List.of(c(OperateurRegle.EGAL, 5, "n"), c(OperateurRegle.DIFFERENT, 5, "n"), c(OperateurRegle.INFERIEUR, 6, "n"),
                    c(OperateurRegle.INFERIEUR_OU_EGAL, 5, "n"), c(OperateurRegle.SUPERIEUR, 5, "n"), c(OperateurRegle.SUPERIEUR_OU_EGAL, 5, "n")))
                    .containsExactly(true, false, true, true, false, true);
            assertThat(List.of(c(OperateurRegle.DANS, List.of("A", "B"), "t"), c(OperateurRegle.HORS, List.of("A"), "t"), c(OperateurRegle.EGAL, true, "b")))
                    .containsExactly(true, false, true);
        }

        @Test
        void nombresEntiersEtDecimauxSontUnMemeNombre_etLesDatesSOrdonnent() {
            assertThat(c(OperateurRegle.EGAL, 5.0, "n")).isTrue();
            assertThat(c(OperateurRegle.DANS, List.of(4.0, 5.0), "n")).isTrue();
            assertThat(c(OperateurRegle.INFERIEUR, "2027-03-01", "d")).isTrue();
            assertThat(c(OperateurRegle.EGAL, "5", "n")).isFalse();
            assertThat(c(OperateurRegle.INFERIEUR, "6", "n")).as("types mêlés : faux").isFalse();
            assertThat(c(OperateurRegle.DANS, "A", "t")).as("une valeur qui n'est pas une liste ne contient rien").isFalse();
        }

        @Test
        void composeTousUnDeEtNon() {
            assertThat(ConditionsRegles.evaluer(new ConditionRegle.Tous(List.of(cmp("n", OperateurRegle.SUPERIEUR, 1), cmp("t", OperateurRegle.EGAL, "A"))), f)).isTrue();
            assertThat(ConditionsRegles.evaluer(new ConditionRegle.UnDe(List.of(cmp("n", OperateurRegle.SUPERIEUR, 9), cmp("t", OperateurRegle.EGAL, "B"))), f)).isFalse();
            assertThat(ConditionsRegles.evaluer(new ConditionRegle.Non(cmp("n", OperateurRegle.SUPERIEUR, 9)), f)).isTrue();
        }

        @Test
        void unFaitManquantEstUneErreurJamaisUnFauxSilencieux() {
            assertThatThrownBy(() -> ConditionsRegles.evaluer(cmp("absent", OperateurRegle.EGAL, 1), f))
                    .isInstanceOf(EffectifsInvalideException.class).hasMessage("fait manquant pour évaluer la règle : « absent »");
            assertThatThrownBy(() -> ConditionsRegles.evaluer(cmp("n", OperateurRegle.EGAL, 1), m("n", null))).hasMessageContaining("fait manquant");
        }
    }

    @Nested
    @DisplayName("messages (E-GN-16)")
    class Messages {
        @Test
        void remplaceLesJetonsParLesFaitsEtLaisseVisiblesCeuxQuiManquent() {
            assertThat(Regles.rendreMessage("{quartsParSemaine} quarts, il en faut {minimum}", m("quartsParSemaine", 2))).isEqualTo("2 quarts, il en faut {minimum}");
            assertThat(Regles.rendreMessage("{x}", m("x", null))).isEqualTo("{x}");
            assertThat(Regles.rendreMessage("{a} {b} {c} {d} $1", m("a", 2.0, "b", 2.5, "c", List.of("X", 3.0), "d", true))).isEqualTo("2 2.5 X,3 true $1");
            assertThat(Regles.texte(Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
        }
    }

    @Nested
    @DisplayName("sélection : le particulier précise le général (E-GN-12)")
    class Selection {
        private final List<VersionRegle> regles = List.of(
                v("org").message("organisme").v(),
                v("tpr").portee(PorteeRegle.STATUT_EMPLOI, "TPR").action(ActionRegle.AVERTIR).message("tpr").v(),
                v("est").portee(PorteeRegle.UNITE, "EST").cle("nuits").condition(cmp("nuitsConsecutives", OperateurRegle.SUPERIEUR, 2)).v(),
                v("vieille").portee(PorteeRegle.CONVENTION, "FSSS").jusqua("2027-02-01").message("fsss").v(),
                v("autre").contexte(ContexteRegle.ABSENCE).cle("preavis").v());

        @Test
        void aCleEgaleLaPorteeLaPlusParticuliereRemplaceLaPlusGenerale() {
            PorteesDuDossier p = PorteesDuDossier.de(List.of("EST", "OPS"), Map.of(PorteesDuDossier.STATUT_EMPLOI, "TPR"));
            assertThat(ids(Regles.selectionner(regles, ContexteRegle.DISPONIBILITE, p, "2027-03-01"))).containsExactly("tpr", "est");
        }

        @Test
        void unePorteeQuiNeVisePasLeDossierNeSAppliquePas_laDateChoisitLaVersion() {
            assertThat(ids(Regles.selectionner(regles, ContexteRegle.DISPONIBILITE, unites("OUEST"), "2027-03-01"))).containsExactly("org");
            assertThat(ids(Regles.selectionner(regles, ContexteRegle.DISPONIBILITE, PorteesDuDossier.de(List.of(), Map.of(PorteesDuDossier.CONVENTION, "FSSS")),
                    "2027-01-15"))).containsExactly("vieille");
            assertThat(Regles.selectionner(regles, ContexteRegle.DISPONIBILITE, unites(), "2026-12-31")).isEmpty();
        }

        @Test
        void aPorteeEgaleLaPlusPrioritaireGagne_chaquePorteeSeReconnait() {
            List<VersionRegle> p = List.of(v("a").priorite(1).v(), v("b").priorite(5).v(), v("c").priorite(3).v());
            assertThat(ids(Regles.selectionner(p, ContexteRegle.DISPONIBILITE, unites(), "2027-03-01"))).containsExactly("b");
            List<VersionRegle> egales = List.of(v("z").cle("zeta").priorite(2).v(), v("a").cle("alpha").priorite(2).v(), v("m").cle("mu").priorite(7).v());
            assertThat(ids(Regles.selectionner(egales, ContexteRegle.DISPONIBILITE, unites(), "2027-03-01"))).containsExactly("m", "a", "z");
            List<VersionRegle> portees = List.of(
                    v("corps").cle("k1").portee(PorteeRegle.CORPS_EMPLOI, "PARA").v(),
                    v("titre").cle("k2").portee(PorteeRegle.TITRE_EMPLOI, "PSA").v(),
                    v("conv").cle("k3").portee(PorteeRegle.CONVENTION, "FSSS").v(),
                    v("unite-sans-code").cle("k4").portee(PorteeRegle.UNITE, null).v());
            PorteesDuDossier dossier = new PorteesDuDossier(List.of(""), "FSSS", null, "PARA", "PSA");
            assertThat(ids(Regles.selectionner(portees, ContexteRegle.DISPONIBILITE, dossier, "2027-03-01"))).containsExactly("titre", "corps", "conv", "unite-sans-code");
            List<VersionRegle> premiere = List.of(v("x").v(), v("y").v());
            assertThat(ids(Regles.selectionner(premiere, ContexteRegle.DISPONIBILITE, unites(), "2027-03-01"))).as("égalité parfaite : la première").containsExactly("x");
        }
    }

    @Nested
    @DisplayName("évaluation : combinaison et motifs")
    class Combinaison {
        @Test
        void laPlusRestrictiveLEmporteTousLesMotifsRestentTriesParSeverite() {
            List<VersionRegle> applicables = List.of(
                    v("a").cle("a").action(ActionRegle.AVERTIR).message("avertir").v(),
                    v("b").cle("b").action(ActionRegle.BLOQUER).message("bloquer {quartsParSemaine}").v(),
                    v("c").cle("c").action(ActionRegle.REFUSER).condition(cmp("quartsParSemaine", OperateurRegle.SUPERIEUR, 10)).v());
            Regles.Verdict r = Regles.evaluer(applicables, m("quartsParSemaine", 2));
            assertThat(r.action()).isEqualTo(ActionRegle.BLOQUER);
            assertThat(r.motifs()).extracting(Regles.Motif::message).containsExactly("bloquer 2", "avertir");
            assertThat(r.motifs().get(0)).isEqualTo(new Regles.Motif("b", 1, "b", ActionRegle.BLOQUER, "bloquer 2"));
            assertThat(r.versions()).containsExactly(new Regles.VersionAppliquee("a", 1), new Regles.VersionAppliquee("b", 1), new Regles.VersionAppliquee("c", 1));
        }

        @Test
        void uneRegleMoinsSevereDeclencheeApresNeRabaissePasLaDecision() {
            Regles.Verdict r = Regles.evaluer(List.of(v("b").cle("b").v(), v("a").cle("a").action(ActionRegle.AVERTIR).v()), m("quartsParSemaine", 1));
            assertThat(r.action()).isEqualTo(ActionRegle.BLOQUER);
            assertThat(r.motifs()).extracting(Regles.Motif::action).containsExactly(ActionRegle.BLOQUER, ActionRegle.AVERTIR);
        }

        @Test
        void sansRegleDeclencheeLaDecisionEstAccepterSansMotif() {
            Regles.Verdict r = Regles.evaluer(List.of(v("r").v()), m("quartsParSemaine", 5));
            assertThat(r.action()).isEqualTo(ActionRegle.ACCEPTER);
            assertThat(r.motifs()).isEmpty();
            assertThat(Regles.evaluer(List.of(), Map.of())).isEqualTo(new Regles.Verdict(ActionRegle.ACCEPTER, List.of(), List.of()));
        }

        @Test
        void ditSiUneActionBloque() {
            assertThat(Arrays.stream(ActionRegle.values()).map(Regles::estBloquant)).containsExactly(false, false, false, true, true, true);
            assertThat(ActionRegle.REFUSER.laPlusRestrictive(ActionRegle.AVERTIR)).isEqualTo(ActionRegle.REFUSER);
        }
    }

    @Nested
    @DisplayName("versions, fiches, aperçu d'impact")
    class VersionsEtApercu {
        @Test
        void laVersionCouranteEstCelleEnVigueurSinonLaVersionAVenir_videSiRetiree() {
            Regle r = new Regle("r", ContexteRegle.DISPONIBILITE, "k", PorteeRegle.ORGANISME, null);
            ContenuRegle c = new ContenuRegle(cmp("quartsParSemaine", OperateurRegle.INFERIEUR, 3), ActionRegle.BLOQUER, "m", 0);
            VersionRegle v1 = new VersionRegle("v1", r, 1, c, "2027-01-01", "2027-06-01");
            VersionRegle v2 = new VersionRegle("v2", r, 2, c, "2027-06-01", null);
            FicheRegle fiche = new FicheRegle(r, List.of(v1, v2));
            assertThat(fiche.courante("2027-03-01")).contains(v1);
            assertThat(fiche.courante("2026-01-01")).contains(v2);
            VersionRegle close = new VersionRegle("v2", r, 2, c, "2027-06-01", "2028-01-01");
            assertThat(new FicheRegle(r, List.of(v1, close)).courante("2026-01-01")).isEqualTo(Optional.empty());
            assertThat(VersionRegle.brouillon(ContexteRegle.ABSENCE, c).enVigueurLe("0001-01-01")).isTrue();
        }

        @Test
        void leContenuSeValideDansLOrdreDuNode() {
            Map<String, Object> e = m("condition", m("fait", "quartsParSemaine", "op", "<", "valeur", 3), "action", "AVERTIR", "message", " m ", "priorite", "4");
            assertThat(ContenuRegle.valider(ContexteRegle.DISPONIBILITE, e))
                    .isEqualTo(new ContenuRegle(cmp("quartsParSemaine", OperateurRegle.INFERIEUR, 3), ActionRegle.AVERTIR, "m", 4));
            assertThatThrownBy(() -> ContenuRegle.valider(ContexteRegle.DISPONIBILITE, m("action", "X"))).hasMessage("une condition est un objet");
        }

        @Test
        void apercuCompteParActionGardeCinqExemplesEtNommeLeDossierFautif() {
            ContenuRegle c = new ContenuRegle(cmp("nuitsConsecutives", OperateurRegle.SUPERIEUR, 2), ActionRegle.AVERTIR, "{nuitsConsecutives} nuits", 0);
            List<Object> dossiers = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                dossiers.add(m("objetId", "d" + i, "faits", m("nuitsConsecutives", 3)));
            }
            dossiers.add(m("faits", m("nuitsConsecutives", 1)));
            dossiers.add(m("faits", m("nuitsConsecutives", 4)));
            ApercuImpact a = ApercuImpact.calculer(ContexteRegle.DISPONIBILITE, c, dossiers);
            assertThat(a.total()).isEqualTo(8);
            assertThat(a.parAction()).containsEntry("AVERTIR", 7).containsEntry("ACCEPTER", 1).containsEntry("REFUSER", 0).hasSize(6);
            assertThat(a.exemples()).hasSize(5).first().isEqualTo(new ApercuImpact.Exemple("d0", "3 nuits"));
            ApercuImpact sansId = ApercuImpact.calculer(ContexteRegle.DISPONIBILITE, c, List.of(m("faits", m("nuitsConsecutives", 3))));
            assertThat(sansId.exemples()).containsExactly(new ApercuImpact.Exemple("", "3 nuits"));
            assertThatThrownBy(() -> ApercuImpact.calculer(ContexteRegle.DISPONIBILITE, c, List.of(m("faits", "x"))))
                    .hasMessage("dossier « undefined » : fait « nuitsConsecutives » manquant");
            assertThatThrownBy(() -> ApercuImpact.calculer(ContexteRegle.DISPONIBILITE, c, List.of(m("objetId", "d9"))))
                    .hasMessage("dossier « d9 » : fait « nuitsConsecutives » manquant");
            assertThatThrownBy(() -> ApercuImpact.calculer(ContexteRegle.DISPONIBILITE, c, List.of(3))).hasMessageContaining("« undefined »");
        }

        @Test
        void uneEvaluationConserveCeQuOnLuiDonne() {
            EvaluationRegle e = new EvaluationRegle("e", "ABSENCE", "absence", "a1", "REFUSER", List.of(), List.of(), Map.of(), "2027-01-01", "h", "u");
            assertThat(e.declencheur()).isEqualTo("u");
            assertThat(new EvaluationRegle.Filtre(null, null, ContexteRegle.ABSENCE).contexte()).isEqualTo(ContexteRegle.ABSENCE);
            Regles.Dossier d = new Regles.Dossier(ContexteRegle.ATTRIBUTION, "quart", "q1", "2027-01-01", null, Map.of(), null);
            assertThat(d.contexte()).isEqualTo(ContexteRegle.ATTRIBUTION);
        }
    }
}

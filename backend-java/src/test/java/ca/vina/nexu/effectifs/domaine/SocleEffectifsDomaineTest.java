package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Domaine du socle des effectifs</strong> (AO 26-015-DO, lots R1 et R13) — jumeau de
 * {@code effectifs-socle-domaine.test.ts} : un référentiel ne se réécrit jamais dans le passé (E-GR-9), un
 * périmètre refuse par défaut (E-GN-8), un rattachement ne crée jamais de cycle, l'export du journal ne
 * transporte pas de formule de tableur.
 */
@DisplayName("Effectifs — domaine du socle")
class SocleEffectifsDomaineTest {

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            r.put((String) kv[i], kv[i + 1]);
        }
        return r;
    }

    @Nested
    @DisplayName("référentiels — registre et saisie")
    class Saisie {
        @Test
        void registreEtTypeInconnu() {
            assertThat(TypeReferentiel.de("unites")).contains(TypeReferentiel.UNITES);
            assertThat(TypeReferentiel.exiger("codes_absence").libelle()).isEqualTo("Codes d'absence");
            assertThatThrownBy(() -> TypeReferentiel.exiger("couleurs")).isInstanceOf(EffectifsInvalideException.class)
                    .hasMessageContaining("référentiel inconnu : « couleurs »").hasMessageContaining("unites, corps_emploi");
            for (TypeReferentiel t : TypeReferentiel.values()) {
                assertThat(t.attributs()).as(t.code()).isNotNull();
            }
        }

        @Test
        void valeurAComportement() {
            Referentiels.Saisie s = Referentiels.validerSaisie(TypeReferentiel.STATUTS_EMPLOI,
                    m("code", " TPR ", "libelleFr", "Temps partiel régulier", "comportement", "TEMPS_PARTIEL_REGULIER", "ordre", "2"));
            assertThat(s.valeur()).isEqualTo(new Referentiels.ValeurSaisie("TPR", "Temps partiel régulier", null, "TEMPS_PARTIEL_REGULIER", 2, Map.of()));
            assertThat(s.references()).isEmpty();
        }

        @Test
        void refusCodeLibelleComportementOrdre() {
            TypeReferentiel st = TypeReferentiel.STATUTS_EMPLOI;
            assertThatThrownBy(() -> Referentiels.validerSaisie(st, m("code", "a b", "libelleFr", "x", "comportement", "TEMPS_COMPLET"))).hasMessageContaining("« code »");
            assertThatThrownBy(() -> Referentiels.validerSaisie(st, m("code", "TC", "libelleFr", " ", "comportement", "TEMPS_COMPLET"))).hasMessageContaining("libellé");
            assertThatThrownBy(() -> Referentiels.validerSaisie(st, m("code", "TC", "libelleFr", "x"))).hasMessageContaining("« comportement » requis");
            assertThatThrownBy(() -> Referentiels.validerSaisie(st, m("code", "TC", "libelleFr", "x", "comportement", "AUTRE"))).hasMessageContaining("TEMPS_COMPLET");
            assertThatThrownBy(() -> Referentiels.validerSaisie(st, m("code", "TC", "libelleFr", "x", "comportement", "TEMPS_COMPLET", "ordre", 1.5))).hasMessageContaining("« ordre »");
            assertThatThrownBy(() -> Referentiels.validerSaisie(st, m("code", "TC", "libelleFr", "x", "comportement", "TEMPS_COMPLET", "ordre", "un"))).hasMessageContaining("« ordre »");
            assertThatThrownBy(() -> Referentiels.validerSaisie(TypeReferentiel.CONVENTIONS, m("code", "C", "libelleFr", "x", "comportement", "X"))).hasMessageContaining("n'a pas de comportement");
            assertThatThrownBy(() -> Referentiels.validerSaisie(TypeReferentiel.CONVENTIONS, m("code", 5, "libelleFr", "x"))).hasMessageContaining("« code »");
        }

        @Test
        void ordreEtLibelleAnglaisEtJourFerie() {
            Referentiels.Saisie s = Referentiels.validerSaisie(TypeReferentiel.CONVENTIONS, m("code", "FSSS", "libelleFr", "Convention FSSS", "libelleEn", "FSSS agreement", "ordre", null));
            assertThat(List.of(s.valeur().ordre(), s.valeur().libelleEn())).containsExactly(0, "FSSS agreement");
            assertThat(Referentiels.validerSaisie(TypeReferentiel.CONVENTIONS, m("code", "B", "libelleFr", "x", "ordre", "")).valeur().ordre()).isZero();
            assertThat(Referentiels.validerSaisie(TypeReferentiel.CONVENTIONS, m("code", "B", "libelleFr", "x", "ordre", 3)).valeur().ordre()).isEqualTo(3);
            assertThat(Referentiels.validerSaisie(TypeReferentiel.JOURS_FERIES, m("code", "2031-06-24", "libelleFr", "Fête nationale")).valeur().code()).isEqualTo("2031-06-24");
            assertThatThrownBy(() -> Referentiels.validerSaisie(TypeReferentiel.JOURS_FERIES, m("code", "NOEL", "libelleFr", "Noël"))).hasMessage("« code » : la date du férié, AAAA-MM-JJ");
        }

        @Test
        void chaqueTypeDAttributEtReferences() {
            Referentiels.Saisie s = Referentiels.validerSaisie(TypeReferentiel.CODES_ABSENCE, m("code", "MAL", "libelleFr", "Maladie", "comportement", "PAYE",
                    "attributs", m("duree", "PONCTUELLE", "planifiable", false, "titres", List.of("PSA", "RMU"))));
            assertThat(s.valeur().attributs()).containsEntry("duree", "PONCTUELLE").containsEntry("planifiable", false).containsEntry("titres", List.of("PSA", "RMU"));
            assertThat(s.references()).containsExactly(new Referentiels.ReferenceAVerifier(TypeReferentiel.TITRES_EMPLOI, "PSA", "titres"),
                    new Referentiels.ReferenceAVerifier(TypeReferentiel.TITRES_EMPLOI, "RMU", "titres"));
            Referentiels.Saisie h = Referentiels.validerSaisie(TypeReferentiel.TYPES_HEURE, m("code", "HS", "libelleFr", "Supp", "comportement", "SUPPLEMENTAIRE",
                    "attributs", m("codePaie", " 050 ", "majorationPourcent", "50")));
            assertThat(h.valeur().attributs()).containsEntry("codePaie", "050").containsEntry("majorationPourcent", 50L);
            assertThat(Referentiels.validerSaisie(TypeReferentiel.TYPES_HEURE, m("code", "HS", "libelleFr", "Supp", "comportement", "SUPPLEMENTAIRE",
                    "attributs", m("majorationPourcent", 12.5))).valeur().attributs()).containsEntry("majorationPourcent", 12.5);
            assertThat(Referentiels.validerSaisie(TypeReferentiel.UNITES, m("code", "EST", "libelleFr", "Est", "attributs", m("parent", "US"))).references())
                    .containsExactly(new Referentiels.ReferenceAVerifier(TypeReferentiel.UNITES, "US", "parent"));
        }

        @Test
        void referencesVidesEtObligatoireNomme() {
            assertThat(Referentiels.validerSaisie(TypeReferentiel.CODES_ABSENCE, m("code", "V", "libelleFr", "Vacances", "comportement", "BANQUE",
                    "attributs", m("duree", "MOYEN_TERME"))).valeur().attributs()).containsEntry("titres", List.of());
            assertThatThrownBy(() -> Referentiels.validerSaisie(TypeReferentiel.CODES_ABSENCE, m("code", "V", "libelleFr", "x", "comportement", "PAYE", "attributs", m())))
                    .hasMessageContaining("« Catégorie de durée » est obligatoire");
        }

        @Test
        void attributsMalTypesHorsBornesInconnus() {
            Map<String, Object> base = m("code", "X", "libelleFr", "x", "comportement", "PAYE");
            TypeReferentiel codes = TypeReferentiel.CODES_ABSENCE;
            assertThatThrownBy(() -> Referentiels.validerSaisie(codes, avec(base, m("duree", "ETERNEL")))).hasMessageContaining("« ETERNEL » inconnu (attendu : PONCTUELLE");
            assertThatThrownBy(() -> Referentiels.validerSaisie(codes, avec(base, m("duree", "PONCTUELLE", "planifiable", "oui")))).hasMessageContaining("vrai ou faux");
            assertThatThrownBy(() -> Referentiels.validerSaisie(codes, avec(base, m("duree", "PONCTUELLE", "titres", "PSA")))).hasMessageContaining("doit être une liste");
            assertThatThrownBy(() -> Referentiels.validerSaisie(codes, avec(base, m("duree", "PONCTUELLE", "couleur", "rouge")))).hasMessageContaining("attribut inconnu pour Codes d'absence : couleur");
            TypeReferentiel plages = TypeReferentiel.PLAGES_HORAIRES;
            Map<String, Object> jour = m("code", "J", "libelleFr", "Jour");
            assertThatThrownBy(() -> Referentiels.validerSaisie(plages, avec(jour, m("debut", "six", "fin", 1080)))).hasMessageContaining("doit être un nombre");
            assertThatThrownBy(() -> Referentiels.validerSaisie(plages, avec(jour, m("debut", Double.NaN, "fin", 1080)))).hasMessageContaining("doit être un nombre");
            assertThatThrownBy(() -> Referentiels.validerSaisie(plages, avec(jour, m("debut", 360, "fin", 2000)))).hasMessageContaining("doit être entre 0 et 1440");
            assertThatThrownBy(() -> Referentiels.validerSaisie(plages, avec(jour, m("debut", -1, "fin", 20)))).hasMessageContaining("doit être entre 0 et 1439");
            assertThatThrownBy(() -> Referentiels.validerSaisie(plages, avec(jour, m("debut", 360, "fin", 360)))).hasMessageContaining("vide");
            assertThat(Referentiels.validerSaisie(plages, avec(jour, m("debut", 1080, "fin", 360))).valeur().attributs()).containsEntry("debut", 1080L).containsEntry("fin", 360L);
            assertThatThrownBy(() -> Referentiels.validerSaisie(plages, m("code", "N", "libelleFr", "Nuit", "attributs", "x"))).hasMessageContaining("« Début (minutes depuis minuit) » est obligatoire");
        }

        private Map<String, Object> avec(Map<String, Object> base, Map<String, Object> attributs) {
            Map<String, Object> r = new HashMap<>(base);
            r.put("attributs", attributs);
            return r;
        }
    }

    @Nested
    @DisplayName("référentiels — datation et non-rétroactivité (E-GR-9)")
    class Datation {
        private final ValeurReferentiel v1 = new ValeurReferentiel("a", "unites", "X", "x", null, null, 0, Map.of(), "2027-01-01", "2027-06-01");
        private final ValeurReferentiel v2 = new ValeurReferentiel("b", "unites", "X", "x", null, null, 0, Map.of(), "2027-06-01", null);

        @Test
        void dateCalendaire() {
            assertThat(Referentiels.dateValide(" 2027-02-28 ", "d")).isEqualTo("2027-02-28");
            assertThatThrownBy(() -> Referentiels.dateValide("2027-2-1", "depuis")).hasMessage("« depuis » : date AAAA-MM-JJ attendue");
            assertThatThrownBy(() -> Referentiels.dateValide("2027-13-40", "d")).hasMessageContaining("AAAA");
            assertThatThrownBy(() -> Referentiels.dateValide(42, "d")).hasMessageContaining("AAAA");
        }

        @Test
        void versionEnVigueurFinExclue() {
            assertThat(Referentiels.enVigueur(List.of(v1, v2), "2027-03-15")).contains(v1);
            assertThat(Referentiels.enVigueur(List.of(v1, v2), "2027-06-01")).contains(v2);
            assertThat(Referentiels.enVigueur(List.of(v1, v2), "2026-12-31")).isEmpty();
            assertThat(Referentiels.versionOuverte(List.of(v1))).isEmpty();
        }

        @Test
        void remplacementEtFermeture() {
            assertThat(Referentiels.planifierRemplacement(List.of(v1, v2), "2027-09-01", "2027-07-01")).isEqualTo("b");
            assertThatThrownBy(() -> Referentiels.planifierRemplacement(List.of(v1, v2), "2027-06-15", "2027-07-01")).hasMessageContaining("rétroactive");
            assertThatThrownBy(() -> Referentiels.planifierRemplacement(List.of(v1, v2), "2027-06-01", "2027-05-01")).hasMessageContaining("après 2027-06-01");
            assertThatThrownBy(() -> Referentiels.planifierRemplacement(List.of(v1), "2027-09-01", "2027-07-01")).hasMessageContaining("fermée");
            assertThat(Referentiels.planifierFermeture(List.of(v1, v2), "2027-12-31", "2027-07-01")).isEqualTo("b");
            assertThatThrownBy(() -> Referentiels.planifierFermeture(List.of(v1, v2), "2027-06-30", "2027-07-01")).hasMessageContaining("rétroactive");
            assertThatThrownBy(() -> Referentiels.planifierFermeture(List.of(v1, v2), "2027-06-01", "2027-01-01")).hasMessageContaining("suivre le début");
            assertThatThrownBy(() -> Referentiels.planifierFermeture(List.of(v1), "2027-12-31", "2027-07-01")).hasMessageContaining("déjà fermée");
        }
    }

    @Nested
    @DisplayName("unités et périmètres (E-GN-7, E-GN-8)")
    class Unites {
        private final List<Perimetres.Unite> unites = List.of(new Perimetres.Unite("US", null), new Perimetres.Unite("OPS", "US"),
                new Perimetres.Unite("EST", "OPS"), new Perimetres.Unite("OUEST", "OPS"), new Perimetres.Unite("CCS", "US"));

        private Perimetres.Perimetre p(String unite, Perimetres.Etendue e, Perimetres.Niveau n, String jusqua) {
            return new Perimetres.Perimetre("p", "u", unite, e, n, "2027-01-01", jusqua);
        }

        @Test
        void conversionsALaFrontiere() {
            assertThat(Perimetres.etendueDe("UNITE")).isEqualTo(Perimetres.Etendue.UNITE);
            assertThat(Perimetres.niveauDe("GESTION")).isEqualTo(Perimetres.Niveau.GESTION);
            assertThatThrownBy(() -> Perimetres.etendueDe("TOUT")).hasMessageContaining("UNITE, SOUS_ARBRE");
            assertThatThrownBy(() -> Perimetres.niveauDe("admin")).hasMessageContaining("LECTURE, PLANIFICATION, GESTION");
        }

        @Test
        void sousArbreEtCycles() {
            assertThat(Perimetres.sousArbre(unites, "OPS")).containsExactlyInAnyOrder("OPS", "EST", "OUEST");
            assertThat(Perimetres.sousArbre(List.of(new Perimetres.Unite("A", "B"), new Perimetres.Unite("B", "A")), "A")).containsExactlyInAnyOrder("A", "B");
            assertThatThrownBy(() -> Perimetres.verifierParent(unites, "OPS", "OPS")).hasMessageContaining("sa propre parente");
            assertThatThrownBy(() -> Perimetres.verifierParent(unites, "OPS", "EST")).hasMessageContaining("cycle");
            Perimetres.verifierParent(unites, "CCS", "EST");
            Perimetres.verifierParent(unites, "OPS", null);
            Perimetres.verifierParent(List.of(new Perimetres.Unite("A", "B"), new Perimetres.Unite("B", "A")), "Z", "A");
        }

        @Test
        void refusParDefautEtendueNiveauDates() {
            assertThat(Perimetres.unitesAccessibles(List.of(), unites, Perimetres.Niveau.LECTURE, "2027-03-01")).isEmpty();
            List<Perimetres.Perimetre> perims = List.of(p("OPS", Perimetres.Etendue.SOUS_ARBRE, Perimetres.Niveau.PLANIFICATION, null),
                    p("CCS", Perimetres.Etendue.UNITE, Perimetres.Niveau.LECTURE, null), p("US", Perimetres.Etendue.SOUS_ARBRE, Perimetres.Niveau.GESTION, "2027-02-01"));
            assertThat(Perimetres.unitesAccessibles(perims, unites, Perimetres.Niveau.LECTURE, "2027-03-01")).containsExactlyInAnyOrder("OPS", "EST", "OUEST", "CCS");
            assertThat(Perimetres.unitesAccessibles(perims, unites, Perimetres.Niveau.PLANIFICATION, "2027-03-01")).doesNotContain("CCS");
            assertThat(Perimetres.unitesAccessibles(perims, unites, Perimetres.Niveau.GESTION, "2027-01-15")).contains("US", "CCS");
            assertThat(Perimetres.unitesAccessibles(perims, unites, Perimetres.Niveau.LECTURE, "2026-12-31")).isEmpty();
        }

        @Test
        void fermetureDUnPerimetre() {
            Perimetres.Perimetre ouvert = p("OPS", Perimetres.Etendue.UNITE, Perimetres.Niveau.LECTURE, null);
            Perimetres.verifierFermeture(ouvert, "2027-03-01", "2027-02-01");
            assertThatThrownBy(() -> Perimetres.verifierFermeture(p("OPS", Perimetres.Etendue.UNITE, Perimetres.Niveau.LECTURE, "2027-02-01"), "2027-03-01", "2027-02-01")).hasMessageContaining("déjà fermé");
            assertThatThrownBy(() -> Perimetres.verifierFermeture(ouvert, "2027-01-15", "2027-02-01")).hasMessageContaining("rétroactive");
            assertThatThrownBy(() -> Perimetres.verifierFermeture(ouvert, "2027-01-01", "2026-12-01")).hasMessageContaining("suivre le début");
        }
    }

    @Nested
    @DisplayName("milieux et preuves (E-GN-17, E-GP-11)")
    class LesMilieux {
        @Test
        void coordonnees() {
            assertThat(Milieux.coordonneesDe(m())).isNull();
            assertThat(Milieux.coordonneesDe(m("latitude", "", "longitude", null))).isNull();
            assertThat(Milieux.coordonneesDe(m("latitude", "45.5", "longitude", -73.6, "rayonMetres", 100))).isEqualTo(new Milieux.Coordonnees(45.5, -73.6, 100));
            assertThatThrownBy(() -> Milieux.coordonneesDe(m("latitude", 45))).hasMessageContaining("se saisissent ensemble");
            assertThatThrownBy(() -> Milieux.coordonneesDe(m("latitude", 91, "longitude", 0, "rayonMetres", 100))).hasMessageContaining("latitude entre -90 et 90");
            assertThatThrownBy(() -> Milieux.coordonneesDe(m("latitude", "x", "longitude", 0, "rayonMetres", 100))).hasMessageContaining("latitude");
            assertThatThrownBy(() -> Milieux.coordonneesDe(m("latitude", 0, "longitude", 181, "rayonMetres", 100))).hasMessageContaining("longitude entre -180 et 180");
            assertThatThrownBy(() -> Milieux.coordonneesDe(m("latitude", 0, "longitude", 0, "rayonMetres", 5))).hasMessageContaining("rayon entier entre 10 et 5000");
            assertThatThrownBy(() -> Milieux.coordonneesDe(m("latitude", 0, "longitude", 0, "rayonMetres", 12.5))).hasMessageContaining("rayon entier");
        }

        @Test
        void distanceEtReseau() {
            assertThat(Milieux.distanceMetres(45.5, -73.6, 45.5, -73.6)).isZero();
            assertThat(Milieux.distanceMetres(45.5, -73.6, 45.59, -73.6)).isEqualTo(10008);
            assertThat(Milieux.cidrValide(" 10.1.0.0/16 ")).isEqualTo("10.1.0.0/16");
            assertThatThrownBy(() -> Milieux.cidrValide("10.1.0.0")).hasMessageContaining("plage réseau invalide");
            assertThatThrownBy(() -> Milieux.cidrValide("300.1.0.0/16")).hasMessageContaining("plage réseau invalide");
            assertThatThrownBy(() -> Milieux.cidrValide("10.1.0.0/40")).hasMessageContaining("plage réseau invalide");
            assertThat(Milieux.dansPlage("10.1.4.5", "10.1.0.0/16")).isTrue();
            assertThat(Milieux.dansPlage("10.2.4.5", "10.1.0.0/16")).isFalse();
        }

        @Test
        void preuves() {
            Milieux.Coordonnees c = new Milieux.Coordonnees(45.5, -73.6, 100);
            List<Milieux.Preuve> p = Milieux.preuvesValides(List.of(m("mecanisme", "GEOREPERAGE", "exige", true),
                    m("mecanisme", "RESEAU", "parametres", m("plages", List.of("10.1.0.0/16"))), m("mecanisme", "CODE_QR_DE_LIEU")), c);
            assertThat(p).extracting(Milieux.Preuve::mecanisme).containsExactly(Milieux.Mecanisme.GEOREPERAGE, Milieux.Mecanisme.RESEAU, Milieux.Mecanisme.CODE_QR_DE_LIEU);
            assertThat(p.get(0).exige()).isTrue();
            assertThat(p.get(1).parametres()).containsEntry("plages", List.of("10.1.0.0/16"));
            assertThatThrownBy(() -> Milieux.preuvesValides(List.of(m("mecanisme", "PIGEON")), c)).hasMessageContaining("mécanisme de preuve inconnu : « PIGEON »");
            assertThatThrownBy(() -> Milieux.preuvesValides(List.of(m("mecanisme", "BORNE"), m("mecanisme", "BORNE")), c)).hasMessageContaining("mécanisme en double : BORNE");
            assertThatThrownBy(() -> Milieux.preuvesValides(List.of(m("mecanisme", "GEOREPERAGE")), null)).hasMessageContaining("le géorepérage exige");
            assertThatThrownBy(() -> Milieux.preuvesValides(List.of(m("mecanisme", "RESEAU", "parametres", "x")), c)).hasMessageContaining("au moins une plage");
            assertThatThrownBy(() -> Milieux.preuvesValides(List.of(m("mecanisme", "RESEAU", "parametres", m("plages", "10.0.0.0/8"))), c)).hasMessageContaining("au moins une plage");
        }
    }

    @Nested
    @DisplayName("journal (E-GN-9, E-GN-10)")
    class Journal {
        @Test
        void formatOrigineEtCsvSur() {
            assertThat(FormatResultats.de(null)).isEqualTo(FormatResultats.JSON);
            assertThat(FormatResultats.de("")).isEqualTo(FormatResultats.JSON);
            assertThat(FormatResultats.de("csv")).isEqualTo(FormatResultats.CSV);
            assertThat(FormatResultats.de("xlsx")).isEqualTo(FormatResultats.XLSX);
            assertThat(FormatResultats.de("pdf")).isEqualTo(FormatResultats.PDF);
            assertThatThrownBy(() -> FormatResultats.de("xml")).isInstanceOf(EffectifsInvalideException.class)
                    .hasMessage("format inconnu : « xml » (attendu : json, csv, xlsx, pdf)");
            assertThat(JournalEffectifs.origineDe("SYSTEME")).isEqualTo(JournalEffectifs.Origine.SYSTEME);
            assertThatThrownBy(() -> JournalEffectifs.origineDe("robot")).hasMessageContaining("origine inconnue");
            assertThat(JournalEffectifs.celluleCsv(null)).isEmpty();
            assertThat(JournalEffectifs.celluleCsv("=SOMME(A1)")).isEqualTo("'=SOMME(A1)");
            assertThat(JournalEffectifs.celluleCsv("a,\"b\"")).isEqualTo("\"a,\"\"b\"\"\"");
            String csv = JournalEffectifs.enCsv(List.of(new JournalEffectifs.Entree("j", "2031-01-01T00:00:00.000Z", "u", JournalEffectifs.Origine.UTILISATEUR,
                    "milieu.cree", "milieu", "m1", null, null, "{\"code\":\"C12\"}")));
            assertThat(csv).isEqualTo("horodatage,origine,acteurId,action,objetType,objetId,uniteCode,avant,apres\n"
                    + "2031-01-01T00:00:00.000Z,UTILISATEUR,u,milieu.cree,milieu,m1,,,\"{\"\"code\"\":\"\"C12\"\"}\"\n");
            assertThat(JournalEffectifs.Consignation.de("a", "t", "i", null, null, 1).systeme()).isNull();
        }
    }

    @Test
    void contexteAdministrateurEtJour() {
        assertThat(new Contexte("t", "u", "admin", "2031-01-15T10:00:00.000Z").estAdministrateur()).isTrue();
        assertThat(new Contexte("t", "u", "superadmin", "2031-01-15T10:00:00.000Z").estAdministrateur()).isTrue();
        assertThat(new Contexte("t", "u", "manager", "2031-01-15T10:00:00.000Z").estAdministrateur()).isFalse();
        assertThat(new Contexte("t", "u", null, "2031-01-15T10:00:00.000Z").aujourdhui()).isEqualTo("2031-01-15");
        assertThat(TypeAttribut.REFERENCES.code()).isEqualTo("references");
        assertThat(new IntrouvableException("x")).hasMessage("x");
        assertThat(new ConflitException("x")).hasMessage("x");
        assertThat(new HorsPerimetreException("x")).hasMessage("x");
    }
}

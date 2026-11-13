package ca.vina.nexu.habilitations.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le domaine des habilitations, sans E/S — jumeau de {@code src/habilitations/domaine.ts}.
 *
 * <p>Ce qui s'éprouve : l'ordre de résolution (nominatif puis profil), le défaut à
 * <strong>refus</strong>, et les échéances qu'un gestionnaire doit voir venir.
 */
@DisplayName("Habilitations — périodes d'effectivité, résolution, consultation")
class HabilitationsTest {

    private static final Habilitations.Objet RAPPORT = new Habilitations.Objet(
            Habilitations.TypeObjet.RAPPORT, "rap-paie", "Rapport de paie", null);
    private static final Habilitations.Objet FORMULAIRE = new Habilitations.Objet(
            Habilitations.TypeObjet.FORMULAIRE, "frm-depense", "Formulaire de dépense", null);

    private static Habilitations.Octroi octroi(Habilitations.Sujet sujet, String id,
                                               Habilitations.Permission p, String du, String au) {
        return new Habilitations.Octroi(sujet, id, Habilitations.TypeObjet.RAPPORT, "rap-paie", p, du, au, null);
    }

    @Nested
    @DisplayName("Les ensembles fermés")
    class Ensembles {

        @Test
        @DisplayName("les trois permissions, les quatre types d'objet, les deux sujets")
        void codes() {
            assertThat(Habilitations.Permission.codes()).containsExactly("aucun", "lecture", "lecture_ecriture");
            assertThat(Habilitations.TypeObjet.codes())
                    .containsExactly("fonctionnalite", "formulaire", "rapport", "composante");
            assertThat(Habilitations.Sujet.de("profil")).isEqualTo(Habilitations.Sujet.PROFIL);
            assertThat(Habilitations.Permission.LECTURE_ECRITURE.force())
                    .isGreaterThan(Habilitations.Permission.LECTURE.force());
        }

        @Test
        @DisplayName("un code hors de l'ensemble est nommé, avec ceux qui existent")
        void inconnus() {
            assertThatThrownBy(() -> Habilitations.Permission.de("ecriture"))
                    .hasMessageContaining("permission inconnue : « ecriture »")
                    .hasMessageContaining("aucun, lecture, lecture_ecriture");
            assertThatThrownBy(() -> Habilitations.TypeObjet.de("ecran"))
                    .hasMessageContaining("type d'objet inconnu : « ecran »");
            assertThatThrownBy(() -> Habilitations.Sujet.de("groupe"))
                    .hasMessageContaining("sujet inconnu : « groupe » (attendu : profil, utilisateur)");
        }
    }

    @Nested
    @DisplayName("Les périodes d'effectivité")
    class Periodes {

        @Test
        @DisplayName("un octroi est effectif bornes comprises, et cesse de lui-même")
        void effectivite() {
            Habilitations.Octroi o = octroi(Habilitations.Sujet.UTILISATEUR, "u1",
                    Habilitations.Permission.LECTURE, "2026-01-01", "2026-06-30");
            assertThat(Habilitations.effectifLe(o, "2026-01-01")).isTrue();
            assertThat(Habilitations.effectifLe(o, "2026-06-30")).isTrue();
            assertThat(Habilitations.effectifLe(o, "2026-07-01")).isFalse();
            assertThat(Habilitations.effectifLe(o, "2025-12-31")).isFalse();
            assertThat(Habilitations.effectifLe(
                    octroi(Habilitations.Sujet.UTILISATEUR, "u1", Habilitations.Permission.LECTURE,
                            "2026-01-01", null), "2030-01-01")).isTrue();
        }

        @Test
        @DisplayName("refuse une fenêtre inversée, une date mal écrite et un sujet vide")
        void refus() {
            assertThatThrownBy(() -> Habilitations.verifierOctroi(octroi(Habilitations.Sujet.UTILISATEUR, "u1",
                    Habilitations.Permission.LECTURE, "2026-06-30", "2026-01-01")))
                    .hasMessageContaining("l'octroi à « u1 » se termine (2026-01-01) avant de commencer (2026-06-30)");
            assertThatThrownBy(() -> Habilitations.verifierOctroi(octroi(Habilitations.Sujet.UTILISATEUR, "u1",
                    Habilitations.Permission.LECTURE, "janvier", null)))
                    .hasMessageContaining("la prise d'effet de « u1 » s'écrit AAAA-MM-JJ");
            assertThatThrownBy(() -> Habilitations.verifierOctroi(octroi(Habilitations.Sujet.UTILISATEUR, " ",
                    Habilitations.Permission.LECTURE, "2026-01-01", null)))
                    .hasMessageContaining("« sujetId » est obligatoire");
        }
    }

    @Nested
    @DisplayName("Résoudre une permission")
    class Resoudre {

        @Test
        @DisplayName("**le nominatif l'emporte sur le profil**, y compris pour retirer un droit")
        void nominatifPrime() {
            List<Habilitations.Octroi> octrois = List.of(
                    octroi(Habilitations.Sujet.PROFIL, "p-paie", Habilitations.Permission.LECTURE_ECRITURE,
                            "2026-01-01", null),
                    octroi(Habilitations.Sujet.UTILISATEUR, "u1", Habilitations.Permission.AUCUN,
                            "2026-01-01", null));
            Habilitations.Resolution r = Habilitations.resoudre("u1", List.of("p-paie"), octrois,
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01");
            assertThat(r.permission()).isEqualTo(Habilitations.Permission.AUCUN);
            assertThat(r.origine()).isEqualTo(Habilitations.Origine.UTILISATEUR);
            assertThat(r.motifs()).singleElement().asString()
                    .isEqualTo("Octroi nominatif : aucun accès jusqu'au terme indéterminé");
        }

        @Test
        @DisplayName("**entre profils, le plus permissif l'emporte** — ajouter un profil ajoute un accès")
        void profilLePlusPermissif() {
            List<Habilitations.Octroi> octrois = List.of(
                    octroi(Habilitations.Sujet.PROFIL, "p-lecture", Habilitations.Permission.LECTURE,
                            "2026-01-01", null),
                    octroi(Habilitations.Sujet.PROFIL, "p-gestion", Habilitations.Permission.LECTURE_ECRITURE,
                            "2026-01-01", null));
            Habilitations.Resolution r = Habilitations.resoudre("u1", List.of("p-lecture", "p-gestion"), octrois,
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01");
            assertThat(r.permission()).isEqualTo(Habilitations.Permission.LECTURE_ECRITURE);
            assertThat(r.origineId()).isEqualTo("p-gestion");
            assertThat(r.motifs()).hasSize(2);
            assertThat(r.motifs().get(1)).contains("le plus permissif l'emporte");
        }

        @Test
        @DisplayName("**le refus est le défaut**, et le motif le dit — ce n'est pas un oubli")
        void refusParDefaut() {
            Habilitations.Resolution r = Habilitations.resoudre("u1", List.of(), List.of(),
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01");
            assertThat(r.permission()).isEqualTo(Habilitations.Permission.AUCUN);
            assertThat(r.origine()).isEqualTo(Habilitations.Origine.DEFAUT);
            assertThat(r.origineId()).isNull();
            assertThat(r.motifs()).singleElement().asString().contains("le refus est le défaut, et non un oubli");
        }

        @Test
        @DisplayName("un octroi expiré ne compte plus : c'est tout l'intérêt de l'exigence 84")
        void expire() {
            List<Habilitations.Octroi> octrois = List.of(octroi(Habilitations.Sujet.PROFIL, "p",
                    Habilitations.Permission.LECTURE_ECRITURE, "2026-01-01", "2026-06-30"));
            assertThat(Habilitations.resoudre("u1", List.of("p"), octrois,
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-06-30").permission())
                    .isEqualTo(Habilitations.Permission.LECTURE_ECRITURE);
            assertThat(Habilitations.resoudre("u1", List.of("p"), octrois,
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-07-01").permission())
                    .isEqualTo(Habilitations.Permission.AUCUN);
        }

        @Test
        @DisplayName("un octroi nominatif porte son motif dans l'explication")
        void motifRepris() {
            List<Habilitations.Octroi> octrois = List.of(new Habilitations.Octroi(
                    Habilitations.Sujet.UTILISATEUR, "u1", Habilitations.TypeObjet.RAPPORT, "rap-paie",
                    Habilitations.Permission.LECTURE, "2026-01-01", "2026-06-30", "Remplacement de congé"));
            assertThat(Habilitations.resoudre("u1", List.of(), octrois,
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01").motifs())
                    .singleElement().asString()
                    .isEqualTo("Octroi nominatif : lecture seule jusqu'au 2026-06-30 — Remplacement de congé");
        }

        @Test
        @DisplayName("refuse un utilisateur vide, un objet vide et une date d'observation mal écrite")
        void refus() {
            assertThatThrownBy(() -> Habilitations.resoudre(" ", List.of(), List.of(),
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01"))
                    .hasMessageContaining("« utilisateurId » est obligatoire");
            assertThatThrownBy(() -> Habilitations.resoudre("u1", List.of(), List.of(),
                    Habilitations.TypeObjet.RAPPORT, " ", "2026-03-01"))
                    .hasMessageContaining("« objetCode » est obligatoire");
            assertThatThrownBy(() -> Habilitations.resoudre("u1", List.of(), List.of(),
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "mars"))
                    .hasMessageContaining("la date d'observation s'écrit AAAA-MM-JJ");
        }
    }

    @Nested
    @DisplayName("Les bords")
    class Bords {

        private static final Habilitations.Objet AUTRE = new Habilitations.Objet(
                Habilitations.TypeObjet.FORMULAIRE, "rap-paie", "Même code, autre type", null);

        @Test
        @DisplayName("**une chaîne vide vaut une absence de terme** : un formulaire qui n'a pas coché « jusqu'au » n'a pas borné l'octroi")
        void termeVide() {
            Habilitations.Octroi sansTerme = octroi(Habilitations.Sujet.PROFIL, "p",
                    Habilitations.Permission.LECTURE, "2026-01-01", "   ");
            assertThat(Habilitations.effectifLe(sansTerme, "2030-01-01")).isTrue();
            Habilitations.verifierOctroi(sansTerme);
            assertThat(Habilitations.resoudre("u1", List.of("p"), List.of(sansTerme),
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2030-01-01").permission())
                    .isEqualTo(Habilitations.Permission.LECTURE);
        }

        @Test
        @DisplayName("un octroi nominatif **destiné à quelqu'un d'autre** ne me concerne pas")
        void nominatifDUnAutre() {
            List<Habilitations.Octroi> octrois = List.of(
                    octroi(Habilitations.Sujet.UTILISATEUR, "u2", Habilitations.Permission.LECTURE_ECRITURE,
                            "2026-01-01", null),
                    octroi(Habilitations.Sujet.PROFIL, "p", Habilitations.Permission.LECTURE, "2026-01-01", null));
            Habilitations.Resolution r = Habilitations.resoudre("u1", List.of("p"), octrois,
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01");
            assertThat(r.origine()).isEqualTo(Habilitations.Origine.PROFIL);
            assertThat(r.permission()).isEqualTo(Habilitations.Permission.LECTURE);
        }

        @Test
        @DisplayName("un octroi d'un profil **auquel je n'appartiens pas** ne me concerne pas")
        void profilEtranger() {
            List<Habilitations.Octroi> octrois = List.of(octroi(Habilitations.Sujet.PROFIL, "p-autre",
                    Habilitations.Permission.LECTURE_ECRITURE, "2026-01-01", null));
            assertThat(Habilitations.resoudre("u1", List.of("p-mien"), octrois,
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01").permission())
                    .isEqualTo(Habilitations.Permission.AUCUN);
        }

        @Test
        @DisplayName("**le code ne suffit pas** : un octroi sur un formulaire du même code n'ouvre pas le rapport")
        void memeCodeAutreType() {
            List<Habilitations.Octroi> octrois = List.of(new Habilitations.Octroi(
                    Habilitations.Sujet.PROFIL, "p", Habilitations.TypeObjet.FORMULAIRE, "rap-paie",
                    Habilitations.Permission.LECTURE_ECRITURE, "2026-01-01", null, null));
            assertThat(Habilitations.resoudre("u1", List.of("p"), octrois,
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01").permission())
                    .isEqualTo(Habilitations.Permission.AUCUN);
            // Sur le formulaire, en revanche, il ouvre bien.
            assertThat(Habilitations.resoudre("u1", List.of("p"), octrois,
                    Habilitations.TypeObjet.FORMULAIRE, "rap-paie", "2026-03-01").permission())
                    .isEqualTo(Habilitations.Permission.LECTURE_ECRITURE);
            assertThat(Habilitations.droitsSurObjet(List.of(AUTRE), octrois, "rap-paie", "2026-03-01")
                    .sujets()).hasSize(1);
        }

        @Test
        @DisplayName("le plus permissif l'emporte **quel que soit l'ordre de déclaration**")
        void ordreIndifferent() {
            Habilitations.Octroi fort = octroi(Habilitations.Sujet.PROFIL, "p-fort",
                    Habilitations.Permission.LECTURE_ECRITURE, "2026-01-01", null);
            Habilitations.Octroi faible = octroi(Habilitations.Sujet.PROFIL, "p-faible",
                    Habilitations.Permission.LECTURE, "2026-01-01", null);
            for (List<Habilitations.Octroi> ordre : List.of(List.of(fort, faible), List.of(faible, fort))) {
                Habilitations.Resolution r = Habilitations.resoudre("u1", List.of("p-fort", "p-faible"),
                        ordre, Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01");
                assertThat(r.permission()).isEqualTo(Habilitations.Permission.LECTURE_ECRITURE);
                assertThat(r.origineId()).isEqualTo("p-fort");
            }
        }

        @Test
        @DisplayName("un octroi nominatif **sans motif** ne laisse pas de tiret pendant dans l'explication")
        void motifVide() {
            List<Habilitations.Octroi> octrois = List.of(new Habilitations.Octroi(
                    Habilitations.Sujet.UTILISATEUR, "u1", Habilitations.TypeObjet.RAPPORT, "rap-paie",
                    Habilitations.Permission.LECTURE, "2026-01-01", "2026-06-30", "   "));
            assertThat(Habilitations.resoudre("u1", List.of(), octrois,
                    Habilitations.TypeObjet.RAPPORT, "rap-paie", "2026-03-01").motifs())
                    .singleElement().asString().isEqualTo("Octroi nominatif : lecture seule jusqu'au 2026-06-30");
        }

        @Test
        @DisplayName("**les échéances écartent** ce qui n'expire pas, ce qui expire trop tard, ce qui est refusé, et ce qui vise un autre")
        void echeancesEcartees() {
            List<Habilitations.Octroi> octrois = List.of(
                    octroi(Habilitations.Sujet.PROFIL, "p", Habilitations.Permission.LECTURE, "2026-01-01", "  "),
                    octroi(Habilitations.Sujet.PROFIL, "p", Habilitations.Permission.LECTURE, "2026-01-01", "2026-12-31"),
                    octroi(Habilitations.Sujet.PROFIL, "p", Habilitations.Permission.AUCUN, "2026-01-01", "2026-03-10"),
                    octroi(Habilitations.Sujet.UTILISATEUR, "u2", Habilitations.Permission.LECTURE, "2026-01-01", "2026-03-10"),
                    octroi(Habilitations.Sujet.PROFIL, "p-autre", Habilitations.Permission.LECTURE, "2026-01-01", "2026-03-10"));
            Habilitations.FicheUtilisateur f = Habilitations.ficheUtilisateur(
                    List.of(new Habilitations.Objet(Habilitations.TypeObjet.RAPPORT, "rap-paie", "R", null)),
                    octrois, "u1", List.of("p"), "2026-03-01");
            assertThat(f.echeances()).isEmpty();
        }

        @Test
        @DisplayName("une date absente et un champ absent se refusent comme une date fausse")
        void absences() {
            assertThatThrownBy(() -> Habilitations.date(null, "la prise d'effet"))
                    .hasMessageContaining("la prise d'effet s'écrit AAAA-MM-JJ");
            assertThatThrownBy(() -> Habilitations.texte(null, "sujetId"))
                    .hasMessageContaining("« sujetId » est obligatoire");
        }
    }

    @Nested
    @DisplayName("Qui peut accéder à cet objet (exigence 90)")
    class DroitsParObjet {

        @Test
        @DisplayName("liste les sujets effectifs, trie par identifiant, et **omet les refus explicites**")
        void liste() {
            List<Habilitations.Octroi> octrois = List.of(
                    octroi(Habilitations.Sujet.PROFIL, "p-zeta", Habilitations.Permission.LECTURE, "2026-01-01", null),
                    octroi(Habilitations.Sujet.PROFIL, "p-alpha", Habilitations.Permission.LECTURE_ECRITURE,
                            "2026-01-01", null),
                    octroi(Habilitations.Sujet.UTILISATEUR, "u-exclu", Habilitations.Permission.AUCUN,
                            "2026-01-01", null),
                    octroi(Habilitations.Sujet.PROFIL, "p-fini", Habilitations.Permission.LECTURE,
                            "2026-01-01", "2026-02-01"));
            Habilitations.DroitParObjet d = Habilitations.droitsSurObjet(List.of(RAPPORT), octrois,
                    "rap-paie", "2026-03-01");
            assertThat(d.objet().libelle()).isEqualTo("Rapport de paie");
            assertThat(d.sujets()).extracting(Habilitations.SujetDroit::sujetId)
                    .containsExactly("p-alpha", "p-zeta");
        }

        @Test
        @DisplayName("refuse un objet inconnu en nommant ceux qui sont déclarés")
        void objetInconnu() {
            assertThatThrownBy(() -> Habilitations.droitsSurObjet(List.of(RAPPORT, FORMULAIRE), List.of(),
                    "rap-absent", "2026-03-01"))
                    .hasMessageContaining("objet inconnu : « rap-absent »")
                    .hasMessageContaining("rap-paie, frm-depense");
        }
    }

    @Nested
    @DisplayName("La fiche d'un utilisateur (exigence 91)")
    class Fiche {

        @Test
        @DisplayName("réunit les droits effectifs et **annonce les échéances à trente jours**")
        void ficheComplete() {
            List<Habilitations.Octroi> octrois = List.of(
                    octroi(Habilitations.Sujet.PROFIL, "p-paie", Habilitations.Permission.LECTURE,
                            "2026-01-01", "2026-03-15"),
                    new Habilitations.Octroi(Habilitations.Sujet.UTILISATEUR, "u1",
                            Habilitations.TypeObjet.FORMULAIRE, "frm-depense",
                            Habilitations.Permission.LECTURE_ECRITURE, "2026-01-01", null, null));
            Habilitations.FicheUtilisateur f = Habilitations.ficheUtilisateur(
                    List.of(RAPPORT, FORMULAIRE), octrois, "u1", List.of("p-paie"), "2026-03-01");
            assertThat(f.droits()).extracting(d -> d.objet().code()).containsExactly("rap-paie", "frm-depense");
            assertThat(f.echeances()).singleElement()
                    .extracting(Habilitations.Echeance::objetCode, Habilitations.Echeance::joursRestants)
                    .containsExactly("rap-paie", 14L);
            assertThat(f.motifs()).hasSize(2);
            assertThat(f.motifs().get(1)).contains("1 droit(s) expirent dans les 30 jours");
        }

        @Test
        @DisplayName("une échéance au-delà de trente jours n'encombre pas la fiche")
        void horizon() {
            List<Habilitations.Octroi> octrois = List.of(octroi(Habilitations.Sujet.PROFIL, "p-paie",
                    Habilitations.Permission.LECTURE, "2026-01-01", "2026-12-31"));
            Habilitations.FicheUtilisateur f = Habilitations.ficheUtilisateur(List.of(RAPPORT), octrois,
                    "u1", List.of("p-paie"), "2026-03-01");
            assertThat(f.droits()).hasSize(1);
            assertThat(f.echeances()).isEmpty();
            assertThat(f.motifs()).singleElement().asString()
                    .isEqualTo("1 objet(s) accessible(s) au 2026-03-01, par 1 profil(s) et les octrois nominatifs");
        }

        @Test
        @DisplayName("sans aucun droit, la fiche le dit plutôt que d'être vide")
        void aucunDroit() {
            Habilitations.FicheUtilisateur f = Habilitations.ficheUtilisateur(List.of(RAPPORT), List.of(),
                    "u1", List.of(), "2026-03-01");
            assertThat(f.droits()).isEmpty();
            assertThat(f.motifs()).singleElement().asString()
                    .contains("Aucun droit effectif au 2026-03-01");
        }
    }
}

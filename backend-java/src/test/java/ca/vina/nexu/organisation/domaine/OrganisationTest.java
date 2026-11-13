package ca.vina.nexu.organisation.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le domaine de la structure datée, sans E/S — jumeau de {@code src/organisation/domaine.ts}.
 *
 * <p>Ce qui s'éprouve ici est ce qu'une structure non datée rendrait impossible : rejouer
 * l'organigramme d'une année passée, et savoir d'où vient une unité.
 */
@DisplayName("Structure organisationnelle datée — versions, réorganisations, filiation")
class OrganisationTest {

    private static Organisation.VersionUnite v(String id, String unite, String code, String parent,
                                               String du, String au) {
        return new Organisation.VersionUnite(id, unite, code, code, parent, du, au,
                Organisation.Operation.CREATION, List.of(), null);
    }

    /** Trois niveaux en vigueur depuis 2020 : ministère › direction générale › direction. */
    private static List<Organisation.VersionUnite> hierarchie() {
        return List.of(
                v("v1", "u1", "MIN", null, "2020-04-01", null),
                v("v2", "u2", "DG", "v1", "2020-04-01", null),
                v("v3", "u3", "DF", "v2", "2020-04-01", null));
    }

    @Nested
    @DisplayName("Les opérations")
    class Operations {

        @Test
        @DisplayName("les quatre codes sont l'ensemble fermé de l'exigence 11")
        void codes() {
            assertThat(Organisation.Operation.codes())
                    .containsExactly("creation", "suppression", "fusion", "fractionnement");
            assertThat(Organisation.Operation.de("fusion")).isEqualTo(Organisation.Operation.FUSION);
            assertThat(Organisation.Operation.FUSION.libelle()).isEqualTo("Fusion");
        }

        @Test
        @DisplayName("une opération inconnue est nommée, avec celles qui existent")
        void inconnue() {
            assertThatThrownBy(() -> Organisation.Operation.de("deplacement"))
                    .isInstanceOf(Organisation.OrganisationInvalideException.class)
                    .hasMessageContaining("opération de réorganisation inconnue : « deplacement »")
                    .hasMessageContaining("creation, suppression, fusion, fractionnement");
        }
    }

    @Nested
    @DisplayName("L'organigramme à une date")
    class Organigramme {

        @Test
        @DisplayName("résout le chemin avec les parents **de cette date**")
        void chemin() {
            List<Organisation.VersionDecrite> o = Organisation.organigrammeLe(hierarchie(), "2024-03-31");
            assertThat(o).extracting(Organisation.VersionDecrite::chemin)
                    .containsExactly("MIN", "MIN › DG", "MIN › DG › DF");
            assertThat(o).extracting(Organisation.VersionDecrite::profondeur).containsExactly(0, 1, 2);
        }

        @Test
        @DisplayName("une version terminée disparaît de l'organigramme du lendemain, pas de celui de la veille")
        void termineeResteLisible() {
            List<Organisation.VersionUnite> versions = List.of(
                    v("v1", "u1", "MIN", null, "2020-04-01", null),
                    v("v2", "u2", "DF", "v1", "2020-04-01", "2025-03-31"));
            assertThat(Organisation.organigrammeLe(versions, "2025-03-31")).hasSize(2);
            assertThat(Organisation.organigrammeLe(versions, "2025-04-01")).hasSize(1);
        }

        @Test
        @DisplayName("**refuse de taire** un rattachement à un parent hors vigueur : l'organigramme serait amputé sans le dire")
        void parentHorsVigueur() {
            List<Organisation.VersionUnite> versions = List.of(
                    v("v1", "u1", "MIN", null, "2020-04-01", "2024-03-31"),
                    v("v2", "u2", "DF", "v1", "2020-04-01", null));
            assertThatThrownBy(() -> Organisation.organigrammeLe(versions, "2025-01-01"))
                    .hasMessageContaining("« DF » est rattachée au 2025-01-01 à un parent qui n'est pas en vigueur");
        }

        @Test
        @DisplayName("refuse une hiérarchie qui boucle")
        void boucle() {
            List<Organisation.VersionUnite> versions = List.of(
                    v("v1", "u1", "A", "v2", "2020-04-01", null),
                    v("v2", "u2", "B", "v1", "2020-04-01", null));
            assertThatThrownBy(() -> Organisation.organigrammeLe(versions, "2024-01-01"))
                    .hasMessageContaining("la hiérarchie boucle sur");
        }

        @Test
        @DisplayName("refuse une date d'observation mal écrite")
        void dateInvalide() {
            assertThatThrownBy(() -> Organisation.organigrammeLe(hierarchie(), "2024"))
                    .hasMessageContaining("la date d'observation s'écrit AAAA-MM-JJ");
        }

        @Test
        @DisplayName("prend le code pour libellé quand le libellé manque")
        void libelleAbsent() {
            Organisation.VersionUnite sans = new Organisation.VersionUnite("v1", "u1", "MIN", "  ", null,
                    "2020-04-01", null, Organisation.Operation.CREATION, List.of(), null);
            assertThat(Organisation.organigrammeLe(List.of(sans), "2024-01-01"))
                    .singleElement().extracting(Organisation.VersionDecrite::chemin).isEqualTo("MIN");
        }
    }

    @Nested
    @DisplayName("L'historique d'une unité")
    class Historique {

        @Test
        @DisplayName("rend les versions de la plus ancienne à la plus récente")
        void ordonne() {
            List<Organisation.VersionUnite> versions = List.of(
                    v("v2", "u1", "DF-2", null, "2025-04-01", null),
                    v("v1", "u1", "DF-1", null, "2020-04-01", "2025-03-31"),
                    v("v9", "u9", "AUTRE", null, "2020-04-01", null));
            assertThat(Organisation.historique(versions, "u1"))
                    .extracting(Organisation.VersionUnite::code).containsExactly("DF-1", "DF-2");
        }

        @Test
        @DisplayName("refuse une unité sans identifiant")
        void sansIdentifiant() {
            assertThatThrownBy(() -> Organisation.historique(hierarchie(), "  "))
                    .hasMessageContaining("« uniteId » est obligatoire");
        }
    }

    @Nested
    @DisplayName("Réorganiser")
    class Reorganiser {

        private final java.util.function.IntFunction<String> ids = i -> "neuf" + i;

        private Organisation.Reorganisation demande(Organisation.Operation op, List<String> sources,
                                                    List<Organisation.Cible> cibles, String motif) {
            return new Organisation.Reorganisation(op, "2025-04-01", sources, cibles, motif);
        }

        private static Organisation.Cible cible(String unite, String code) {
            return new Organisation.Cible(unite, code, code, null);
        }

        @Test
        @DisplayName("**la bascule est sans trou ni chevauchement** : la source finit la veille, la cible commence le jour même")
        void bascule() {
            Organisation.ResultatReorganisation r = Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.FUSION, List.of("v2", "v3"),
                            List.of(cible("u4", "DGF")), "Fusion des directions générales et des finances"),
                    ids);
            assertThat(r.terminees()).extracting(Organisation.VersionTerminee::valideAu)
                    .containsOnly("2025-03-31");
            assertThat(r.creees()).singleElement()
                    .extracting(Organisation.VersionUnite::valideDu, Organisation.VersionUnite::issueDe)
                    .containsExactly("2025-04-01", List.of("v2", "v3"));
            assertThat(r.motifs()).first().asString()
                    .isEqualTo("Fusion au 2025-04-01 : 2 version(s) terminée(s) le 2025-03-31, 1 créée(s)");
        }

        @Test
        @DisplayName("**chaque opération a sa forme** : une fusion à une source est un déplacement, pas une fusion")
        void formes() {
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.FUSION, List.of("v2"), List.of(cible("u4", "X")),
                            "Motif suffisamment long"), ids))
                    .hasMessageContaining("une fusion attend au moins deux sources et une cible ; reçu 1 source(s) et 1 cible(s)");
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.CREATION, List.of("v2"), List.of(cible("u4", "X")),
                            "Motif suffisamment long"), ids))
                    .hasMessageContaining("une création attend aucune source et une cible");
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.SUPPRESSION, List.of("v2"), List.of(cible("u4", "X")),
                            "Motif suffisamment long"), ids))
                    .hasMessageContaining("une suppression attend une source et aucune cible");
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.FRACTIONNEMENT, List.of("v2"), List.of(cible("u4", "X")),
                            "Motif suffisamment long"), ids))
                    .hasMessageContaining("un fractionnement attend une source et au moins deux cibles");
        }

        @Test
        @DisplayName("une création sans source et une suppression sans cible passent")
        void formesValides() {
            assertThat(Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.CREATION, List.of(), List.of(cible("u4", "NEUVE")),
                            "Création de la direction des ressources"), ids).creees()).hasSize(1);
            assertThat(Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.SUPPRESSION, List.of("v3"), List.of(),
                            "Abolition de la direction des finances"), ids).terminees()).hasSize(1);
            List<Organisation.Cible> deux = new ArrayList<>(List.of(cible("u4", "A"), cible("u5", "B")));
            assertThat(Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.FRACTIONNEMENT, List.of("v3"), deux,
                            "Scission de la direction des finances"), ids).creees()).hasSize(2);
        }

        @Test
        @DisplayName("**exige un motif écrit** : c'est lui qui expliquera la fusion trois ans plus tard")
        void motif() {
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.SUPPRESSION, List.of("v3"), List.of(), "  "), ids))
                    .hasMessageContaining("« motif » est obligatoire");
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.SUPPRESSION, List.of("v3"), List.of(), "trop bref"), ids))
                    .hasMessageContaining("au moins dix caractères");
        }

        @Test
        @DisplayName("refuse une source introuvable, déjà terminée, ou pas encore commencée")
        void sourcesHorsPortee() {
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.SUPPRESSION, List.of("vX"), List.of(), "Motif assez long"), ids))
                    .hasMessageContaining("version introuvable : vX");
            List<Organisation.VersionUnite> terminee = List.of(v("v1", "u1", "MIN", null, "2020-04-01", "2024-03-31"));
            assertThatThrownBy(() -> Organisation.reorganiser(terminee,
                    demande(Organisation.Operation.SUPPRESSION, List.of("v1"), List.of(), "Motif assez long"), ids))
                    .hasMessageContaining("« MIN » est déjà terminée le 2024-03-31");
            List<Organisation.VersionUnite> future = List.of(v("v1", "u1", "MIN", null, "2026-04-01", null));
            assertThatThrownBy(() -> Organisation.reorganiser(future,
                    demande(Organisation.Operation.SUPPRESSION, List.of("v1"), List.of(), "Motif assez long"), ids))
                    .hasMessageContaining("« MIN » ne commence que le 2026-04-01");
        }

        @Test
        @DisplayName("refuse une date d'effet mal écrite, une cible sans code et une cible sans unité")
        void champsCibles() {
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    new Organisation.Reorganisation(Organisation.Operation.CREATION, "avril",
                            List.of(), List.of(cible("u4", "X")), "Motif assez long"), ids))
                    .hasMessageContaining("la date d'effet s'écrit AAAA-MM-JJ");
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.CREATION, List.of(), List.of(cible("u4", " ")),
                            "Motif assez long"), ids)).hasMessageContaining("« code » est obligatoire");
            assertThatThrownBy(() -> Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.CREATION, List.of(), List.of(cible(" ", "X")),
                            "Motif assez long"), ids)).hasMessageContaining("« uniteId » est obligatoire");
        }

        @Test
        @DisplayName("le motif accompagne les versions créées : la base le portera de toute façon")
        void motifPorte() {
            assertThat(Organisation.reorganiser(hierarchie(),
                    demande(Organisation.Operation.CREATION, List.of(), List.of(cible("u4", "NEUVE")),
                            "Création de la direction des ressources"), ids).creees())
                    .singleElement().extracting(Organisation.VersionUnite::motif)
                    .isEqualTo("Création de la direction des ressources");
        }
    }

    @Nested
    @DisplayName("Les bords")
    class Bords {

        private final java.util.function.IntFunction<String> ids = i -> "neuf" + i;

        @Test
        @DisplayName("**une chaîne vide vaut une absence** : ni terme, ni libellé, ni parent")
        void chaineVideVautAbsence() {
            Organisation.VersionUnite sansTerme = new Organisation.VersionUnite("v1", "u1", "MIN", "  ", "  ",
                    "2020-04-01", "  ", Organisation.Operation.CREATION, List.of(), null);
            assertThat(Organisation.enVigueurLe(sansTerme, "2030-01-01")).isTrue();
            // Libellé vide → le code fait office de libellé ; parent vide → racine, pas parent inconnu.
            assertThat(Organisation.organigrammeLe(List.of(sansTerme), "2030-01-01"))
                    .singleElement().extracting(Organisation.VersionDecrite::chemin).isEqualTo("MIN");
        }

        @Test
        @DisplayName("une version **terminée par une chaîne vide** se réorganise encore ; terminée par une date, non")
        void termeVideAuxSources() {
            Organisation.VersionUnite ouverte = new Organisation.VersionUnite("v1", "u1", "MIN", "MIN", null,
                    "2020-04-01", "   ", Organisation.Operation.CREATION, List.of(), null);
            assertThat(Organisation.reorganiser(List.of(ouverte),
                    new Organisation.Reorganisation(Organisation.Operation.SUPPRESSION, "2025-04-01",
                            List.of("v1"), List.of(), "Abolition de l'unité"), ids).terminees()).hasSize(1);
        }

        @Test
        @DisplayName("une cible **sans libellé** prend son code, et la copie ne perd rien")
        void cibleSansLibelle() {
            Organisation.ResultatReorganisation r = Organisation.reorganiser(List.of(),
                    new Organisation.Reorganisation(Organisation.Operation.CREATION, "2025-04-01", List.of(),
                            List.of(new Organisation.Cible("u9", "NEUVE", "   ", null)),
                            "Création de la direction neuve"), ids);
            assertThat(r.creees()).singleElement()
                    .extracting(Organisation.VersionUnite::libelle).isEqualTo("NEUVE");
        }

        @Test
        @DisplayName("**les quatre formes se refusent dans les deux sens** : trop peu et trop")
        void formesDansLesDeuxSens() {
            java.util.function.BiFunction<Organisation.Operation, int[], Throwable> refus = (op, n) -> {
                List<String> sources = new ArrayList<>();
                for (int i = 0; i < n[0]; i++) {
                    sources.add("v" + (i + 1));
                }
                List<Organisation.Cible> cibles = new ArrayList<>();
                for (int i = 0; i < n[1]; i++) {
                    cibles.add(new Organisation.Cible("u" + i, "C" + i, "C" + i, null));
                }
                try {
                    Organisation.reorganiser(hierarchie(), new Organisation.Reorganisation(op, "2025-04-01",
                            sources, cibles, "Un motif suffisamment long"), ids);
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            };
            // Une création avec deux cibles, une suppression sans source, une fusion à trois cibles,
            // un fractionnement sans source : chacune rate sa forme par l'autre bout.
            assertThat(refus.apply(Organisation.Operation.CREATION, new int[] {0, 2})).isNotNull();
            assertThat(refus.apply(Organisation.Operation.SUPPRESSION, new int[] {0, 0})).isNotNull();
            assertThat(refus.apply(Organisation.Operation.FUSION, new int[] {2, 3})).isNotNull();
            assertThat(refus.apply(Organisation.Operation.FRACTIONNEMENT, new int[] {0, 2})).isNotNull();
        }

        @Test
        @DisplayName("une filiation qui **repasse par le même ascendant** ne le compte qu'une fois")
        void ascendantPartage() {
            List<Organisation.VersionUnite> versions = List.of(
                    v("souche", "u0", "SOUCHE", null, "2020-04-01", "2022-03-31"),
                    new Organisation.VersionUnite("a", "u1", "A", "A", null, "2022-04-01", "2024-03-31",
                            Organisation.Operation.FRACTIONNEMENT, List.of("souche"), null),
                    new Organisation.VersionUnite("b", "u2", "B", "B", null, "2022-04-01", "2024-03-31",
                            Organisation.Operation.FRACTIONNEMENT, List.of("souche"), null),
                    new Organisation.VersionUnite("ab", "u3", "AB", "AB", null, "2024-04-01", null,
                            Organisation.Operation.FUSION, List.of("a", "b"), null));
            assertThat(Organisation.filiation(versions, "ab").ascendants())
                    .extracting(Organisation.Ascendant::code).containsExactly("A", "B", "SOUCHE");
        }

        @Test
        @DisplayName("une date absente et un champ absent se refusent comme une date fausse")
        void absences() {
            assertThatThrownBy(() -> Organisation.enVigueurLe(
                    v("v1", "u1", "MIN", null, "2020-04-01", null), null))
                    .hasMessageContaining("la date d'observation s'écrit AAAA-MM-JJ");
            assertThatThrownBy(() -> Organisation.historique(hierarchie(), null))
                    .hasMessageContaining("« uniteId » est obligatoire");
        }
    }

    @Nested
    @DisplayName("La filiation")
    class Filiation {

        @Test
        @DisplayName("remonte les ascendants, même à travers deux réorganisations successives")
        void remonte() {
            List<Organisation.VersionUnite> versions = List.of(
                    v("v1", "u1", "A", null, "2020-04-01", "2023-03-31"),
                    v("v2", "u2", "B", null, "2020-04-01", "2023-03-31"),
                    new Organisation.VersionUnite("v3", "u3", "AB", "AB", null, "2023-04-01", "2025-03-31",
                            Organisation.Operation.FUSION, List.of("v1", "v2"), "Fusion de A et B"),
                    new Organisation.VersionUnite("v4", "u4", "ABC", "ABC", null, "2025-04-01", null,
                            Organisation.Operation.FUSION, List.of("v3"), "Reprise par C"));
            Organisation.Filiation f = Organisation.filiation(versions, "v4");
            assertThat(f.code()).isEqualTo("ABC");
            assertThat(f.ascendants()).extracting(Organisation.Ascendant::code).containsExactly("AB", "A", "B");
            assertThat(f.ascendants()).first()
                    .extracting(Organisation.Ascendant::operation, Organisation.Ascendant::effetLe)
                    .containsExactly(Organisation.Operation.FUSION, "2023-04-01");
        }

        @Test
        @DisplayName("une version sans ascendant rend une filiation vide, pas une erreur")
        void sansAscendant() {
            assertThat(Organisation.filiation(hierarchie(), "v1").ascendants()).isEmpty();
        }

        @Test
        @DisplayName("un ascendant cité mais absent est ignoré plutôt que de rompre la lecture")
        void ascendantAbsent() {
            List<Organisation.VersionUnite> versions = List.of(
                    new Organisation.VersionUnite("v4", "u4", "ABC", "ABC", null, "2025-04-01", null,
                            Organisation.Operation.FUSION, List.of("disparue"), null));
            assertThat(Organisation.filiation(versions, "v4").ascendants()).isEmpty();
        }

        @Test
        @DisplayName("refuse une version introuvable, et un identifiant vide")
        void introuvable() {
            assertThatThrownBy(() -> Organisation.filiation(hierarchie(), "vX"))
                    .hasMessageContaining("version introuvable : vX");
            assertThatThrownBy(() -> Organisation.filiation(hierarchie(), " "))
                    .hasMessageContaining("« versionId » est obligatoire");
        }
    }
}

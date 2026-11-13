package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("P-TN-2 — les annuaires d'un organisme : validation et admission")
class AnnuairesTest {

    private static final Set<String> ROLES = Set.of("admin", "manager", "employee", "superadmin");
    private static final String GUID = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String CLIENT = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    private static Annuaires.Saisie saisie(List<Annuaires.Correspondance> regles, String defaut) {
        return new Annuaires.Saisie("  Siège social ", GUID.toUpperCase(), CLIENT, "ANNUAIRE_SIEGE", List.of("Urgences-Sante.QC.ca", " ", "urgences-sante.qc.ca"),
                null, regles, defaut, true);
    }

    private static Annuaires.Annuaire valide(List<Annuaires.Correspondance> regles, String defaut) {
        return Annuaires.valider("ann-1", "T1", saisie(regles, defaut), ROLES);
    }

    private static List<String> motifs(Annuaires.Saisie s) {
        try {
            Annuaires.valider("ann-1", "T1", s, ROLES);
            throw new AssertionError("saisie acceptée");
        } catch (Annuaires.AnnuaireInvalideException e) {
            assertThat(e.getMessage()).isEqualTo(String.join(" ; ", e.motifs()));
            return e.motifs();
        }
    }

    @Test
    @DisplayName("une saisie complète est normalisée : nom rogné, GUID et domaines en minuscules, doublons écartés, groupes par défaut")
    void normalisation() {
        Annuaires.Annuaire a = valide(List.of(new Annuaires.Correspondance(" g-admin ", " admin ")), null);
        assertThat(a.nom()).isEqualTo("Siège social");
        assertThat(a.locataireEntra()).isEqualTo(GUID);
        assertThat(a.domaines()).containsExactly("urgences-sante.qc.ca");
        assertThat(a.revendication()).isEqualTo(Annuaires.Revendication.GROUPES);
        assertThat(a.correspondances()).containsExactly(new Annuaires.Correspondance("g-admin", "admin"));
        assertThat(a.roleParDefaut()).isEmpty();
        assertThat(a.autorite()).isEqualTo("https://login.microsoftonline.com/" + GUID);
        assertThat(a.actif()).isTrue();
        assertThat(Annuaires.valider("ann-2", "T1", new Annuaires.Saisie("Sous-traitant", "partenaire.onmicrosoft.com", CLIENT, "ANNUAIRE_P",
                List.of("partenaire.ca"), "roles", null, " employee ", false), ROLES))
                .satisfies(b -> {
                    assertThat(b.revendication()).isEqualTo(Annuaires.Revendication.ROLES);
                    assertThat(b.roleParDefaut()).contains("employee");
                    assertThat(b.correspondances()).isEmpty();
                    assertThat(b.actif()).isFalse();
                });
    }

    @Test
    @DisplayName("une saisie vide dit tout ce qui manque, en une fois")
    void saisieVide() {
        assertThat(motifs(new Annuaires.Saisie(null, null, null, null, null, null, null, "  ", true))).containsExactly(
                "le nom est requis (80 caractères au plus)",
                "le locataire Entra doit être un GUID ou un domaine (p. ex. organisme.onmicrosoft.com)",
                "l'identifiant d'application (client) doit être un GUID",
                "la variable du secret doit commencer par ANNUAIRE_ (majuscules, chiffres, _)",
                "déclarer au moins un domaine de courriel admis",
                "déclarer au moins une correspondance groupe → rôle, ou un rôle par défaut");
    }

    @Test
    @DisplayName("une autorité ouverte à tout compte Microsoft est refusée, quelle que soit sa casse")
    void autoriteOuverte() {
        for (String ouverte : List.of("common", "Organizations", "consumers")) {
            Annuaires.Saisie s = new Annuaires.Saisie("A", ouverte, CLIENT, "ANNUAIRE_A", List.of("a.ca"), "groups", null, "employee", true);
            assertThat(motifs(s)).containsExactly("le locataire « " + ouverte.toLowerCase() + " » admet n'importe quel compte Microsoft : déclarer celui de l'organisme");
        }
    }

    @Test
    @DisplayName("nom trop long, variable hors préfixe, domaine et revendication invalides")
    void champsInvalides() {
        Annuaires.Saisie s = new Annuaires.Saisie("x".repeat(81), GUID, CLIENT, "NEXU_JWT_SECRET", List.of("localhost"), "upn", null, "employee", true);
        assertThat(motifs(s)).containsExactly(
                "le nom est requis (80 caractères au plus)",
                "la variable du secret doit commencer par ANNUAIRE_ (majuscules, chiffres, _)",
                "domaine invalide : « localhost »",
                "revendication inconnue : « upn » (attendu : groups, roles)");
    }

    @Test
    @DisplayName("correspondances : groupe requis, rôle attribuable, jamais superadmin, pas de doublon ; défaut attribuable")
    void correspondancesInvalides() {
        List<Annuaires.Correspondance> regles = List.of(new Annuaires.Correspondance(" ", "admin"), new Annuaires.Correspondance(null, "admin"),
                new Annuaires.Correspondance("g".repeat(257), "admin"), new Annuaires.Correspondance("g1", "pilote"),
                new Annuaires.Correspondance("g2", "superadmin"), new Annuaires.Correspondance("g3", null),
                new Annuaires.Correspondance("g4", "manager"), new Annuaires.Correspondance("g4", "admin"));
        assertThat(motifs(saisie(regles, "superadmin"))).containsExactly(
                "un groupe est requis pour chaque correspondance",
                "un groupe est requis pour chaque correspondance",
                "un groupe est requis pour chaque correspondance",
                "rôle non attribuable pour le groupe « g1 » : « pilote »",
                "rôle non attribuable pour le groupe « g2 » : « superadmin »",
                "rôle non attribuable pour le groupe « g3 » : «  »",
                "le groupe « g4 » est déclaré deux fois",
                "rôle par défaut non attribuable : « superadmin »");
    }

    @Test
    @DisplayName("la revendication se lit par son code ; un code inconnu est un refus nommé")
    void revendication() {
        assertThat(Arrays.stream(Annuaires.Revendication.values()).map(Annuaires.Revendication::code)).containsExactly("groups", "roles");
        assertThat(Annuaires.Revendication.de("roles")).isEqualTo(Annuaires.Revendication.ROLES);
        assertThatThrownBy(() -> Annuaires.Revendication.de("x")).isInstanceOf(Annuaires.AnnuaireInvalideException.class)
                .hasMessage("revendication inconnue : « x » (attendu : groups, roles)");
    }

    @Test
    @DisplayName("le rôle : la première règle déclarée l'emporte, quel que soit l'ordre des groupes reçus ; sinon le défaut")
    void roleDesGroupes() {
        Annuaires.Annuaire a = valide(List.of(new Annuaires.Correspondance("g-admin", "admin"), new Annuaires.Correspondance("g-gest", "manager")), null);
        assertThat(a.roleDe(Map.of("groups", List.of("g-gest", "g-admin")))).contains("admin");
        assertThat(a.roleDe(Map.of("groups", List.of("g-gest")))).contains("manager");
        assertThat(a.roleDe(Map.of("groups", "g-gest"))).contains("manager");
        assertThat(a.roleDe(Map.of("groups", List.of("autre")))).isEmpty();
        assertThat(a.roleDe(Map.of())).isEmpty();
        Annuaires.Annuaire parDefaut = valide(List.of(new Annuaires.Correspondance("g-admin", "admin")), "employee");
        assertThat(parDefaut.roleDe(Map.of())).contains("employee");
    }

    @Test
    @DisplayName("admission : domaine admis et rôle trouvé ; sinon un refus qui dit pourquoi")
    void admission() {
        Annuaires.Annuaire a = valide(List.of(new Annuaires.Correspondance("g-admin", "admin")), null);
        assertThat(Annuaires.admettre(a, "Julie@URGENCES-SANTE.qc.ca", Map.of("groups", List.of("g-admin")))).isEqualTo("admin");
        assertThatThrownBy(() -> Annuaires.admettre(a, "invite@gmail.com", Map.of("groups", List.of("g-admin"))))
                .isInstanceOf(Annuaires.ConnexionRefuseeException.class)
                .hasMessage("le domaine de « invite@gmail.com » n'est pas admis par l'annuaire « Siège social »");
        assertThatThrownBy(() -> Annuaires.admettre(a, "sansarobase", Map.of())).isInstanceOf(Annuaires.ConnexionRefuseeException.class);
        assertThatThrownBy(() -> Annuaires.admettre(a, "@urgences-sante.qc.ca", Map.of())).isInstanceOf(Annuaires.ConnexionRefuseeException.class);
        assertThatThrownBy(() -> Annuaires.admettre(a, "paul@urgences-sante.qc.ca", Map.of("groups", List.of())))
                .isInstanceOf(Annuaires.ConnexionRefuseeException.class)
                .hasMessage("aucun groupe de « paul@urgences-sante.qc.ca » ne donne accès dans l'annuaire « Siège social »");
        assertThat(new Annuaires.Annuaire("x", "T", "n", "l", "c", "v", List.of(), Annuaires.Revendication.ROLES, List.of(), Optional.empty(), true)
                .admet("a@b.ca")).isFalse();
    }
}

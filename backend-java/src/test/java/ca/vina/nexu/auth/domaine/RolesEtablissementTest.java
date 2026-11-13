package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Rôles d'établissement — codes, rôles livrés, invariants de surcharge")
class RolesEtablissementTest {

    @Test
    void unCodeEstNormaliseEtBorne() {
        assertThat(RolesEtablissement.codeValide("  Doyen-2 ")).isEqualTo("doyen-2");
        assertThatThrownBy(() -> RolesEtablissement.codeValide("2doyen")).hasMessage("code must be 2-32 chars: lowercase letter first, then a-z 0-9 _ -");
        assertThatThrownBy(() -> RolesEtablissement.codeValide(null)).hasMessageStartingWith("code must be");
        assertThatThrownBy(() -> RolesEtablissement.codeValide("Admin")).hasMessage("'admin' is a built-in role");
        assertThatThrownBy(() -> RolesEtablissement.codeValide("superadmin")).hasMessage("'superadmin' is a built-in role");
        assertThat(RolesEtablissement.libelleValide("  Doyen ")).isEqualTo("Doyen");
        assertThatThrownBy(() -> RolesEtablissement.libelleValide(" ")).hasMessage("label required");
        assertThatThrownBy(() -> RolesEtablissement.libelleValide(null)).hasMessage("label required");
    }

    @Test
    void lesSurchargesRespectentLesTroisInvariants() {
        assertThatThrownBy(() -> RolesEtablissement.verifierSurcharge("employee", false, true)).hasMessage("unknown capability");
        assertThatThrownBy(() -> RolesEtablissement.verifierSurcharge("superadmin", true, true)).hasMessage("le superadmin détient toutes les capacités");
        assertThatThrownBy(() -> RolesEtablissement.verifierSurcharge("admin", true, false))
                .hasMessage("les droits de l'administrateur ne peuvent pas être retirés : personne ne pourrait les rendre");
        assertThatCode(() -> RolesEtablissement.verifierSurcharge("admin", true, true)).doesNotThrowAnyException();
        assertThatCode(() -> RolesEtablissement.verifierSurcharge("employee", true, false)).doesNotThrowAnyException();
    }

    @Test
    void unRoleLivreNeSeRenommeNiNeSeSupprimeEtUnRolePorteResteEnPlace() {
        assertThatThrownBy(() -> RolesEtablissement.exigerPersonnalise("rh", "renamed")).hasMessage("built-in role cannot be renamed");
        assertThatCode(() -> RolesEtablissement.exigerPersonnalise("doyen", "deleted")).doesNotThrowAnyException();
        assertThatThrownBy(() -> RolesEtablissement.exigerSansPorteurs(2)).hasMessage("2 compte(s) portent encore ce rôle : reclassez-les d'abord");
        assertThatCode(() -> RolesEtablissement.exigerSansPorteurs(0)).doesNotThrowAnyException();
        assertThat(new RolesEtablissement.Role("doyen", "Doyen", false, true, List.of("x")).vue())
                .containsEntry("code", "doyen").containsEntry("builtin", false).containsEntry("capabilities", List.of("x"));
        assertThat(RolesEtablissement.LIBELLES_LIVRES).containsEntry("rh", "Ressources humaines");
    }
}

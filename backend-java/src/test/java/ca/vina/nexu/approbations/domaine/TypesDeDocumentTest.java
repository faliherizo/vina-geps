package ca.vina.nexu.approbations.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Approbations — catalogue des types de document et suggestion de délégataires")
class TypesDeDocumentTest {

    @Test
    @DisplayName("une création déduit son code du libellé, garde les traductions ou retombe sur le français, et refuse les formes invalides")
    void creation() {
        TypesDeDocument.Nouveau n = TypesDeDocument.nouveau(Map.of("labelFr", "Entente de service", "sortOrder", 5, "labelEn", "Service agreement"));
        assertThat(n.code()).isEqualTo("entente_de_service");
        assertThat(n.labelEn()).isEqualTo("Service agreement");
        assertThat(n.labelMg()).isEqualTo("Entente de service");
        assertThat(n.sortOrder()).isEqualTo(5);
        assertThat(TypesDeDocument.nouveau(Map.of("labelFr", "Éé", "sortOrder", "7")).sortOrder()).isEqualTo(99);
        assertThat(TypesDeDocument.nouveau(Map.of("labelFr", "2026 budget")).code()).isEqualTo("t_2026_budget");
        assertThat(TypesDeDocument.nouveau(Map.of("labelFr", "Bon", "code", " Effectifs.Feuille ")).code()).isEqualTo("effectifs.feuille");
        assertThatThrownBy(() -> TypesDeDocument.nouveau(Map.of("labelFr", "x"))).hasMessage("labelFr must be at least 2 characters");
        assertThatThrownBy(() -> TypesDeDocument.nouveau(Map.of("labelFr", "Ok", "code", "9x")))
                .hasMessageStartingWith("code must be lowercase alphanumeric");
    }

    @Test
    @DisplayName("une modification n'applique que les clés présentes ; un type système ne se supprime pas")
    void modificationEtSuppression() {
        Map<String, Object> patch = new HashMap<>();
        patch.put("labelEn", " Contract ");
        patch.put("sortOrder", "12");
        patch.put("active", 0);
        TypesDeDocument.Modification m = TypesDeDocument.modification(patch);
        assertThat(m.labelFr()).isNull();
        assertThat(m.labelEn()).isEqualTo("Contract");
        assertThat(m.sortOrder()).isEqualTo(12);
        assertThat(m.active()).isFalse();
        assertThat(TypesDeDocument.modification(Map.of("active", "oui", "sortOrder", 3.0)).active()).isTrue();
        assertThat(TypesDeDocument.modification(Map.of("sortOrder", "abc")).sortOrder()).isZero();
        assertThatThrownBy(() -> TypesDeDocument.modification(Map.of("labelFr", " "))).hasMessage("labelFr must be at least 2 characters");
        TypesDeDocument.Type systeme = new TypesDeDocument.Type("i", "t", "contract", "Contrat", "Contract", "F", true, 10, true, "le");
        assertThatThrownBy(() -> TypesDeDocument.verifierSuppression(systeme)).hasMessageContaining("deactivate it instead");
        TypesDeDocument.verifierSuppression(new TypesDeDocument.Type("i", "t", "x", "X", "X", "X", false, 1, true, "le"));
        assertThat(TypesDeDocument.TYPES_SYSTEME).extracting(TypesDeDocument.Systeme::code).contains("contract", "effectifs.pointage");
    }

    @Test
    @DisplayName("la suggestion exige trois caractères, écarte les inactifs et soi-même, cherche dans le nom, l'identifiant et le courriel")
    void suggestion() {
        List<Delegataires.Compte> comptes = List.of(
                new Delegataires.Compte("u1", "alex", "alex@ex.ca", "employee", true, "Alex Bérubé"),
                new Delegataires.Compte("u2", "bob", null, "manager", false, "Bob Martin"),
                new Delegataires.Compte("u3", "chantal", "c@alexandrie.ca", "manager", true, "Chantal Roy"),
                new Delegataires.Compte("u4", "moi", null, "admin", true, "Alex Moi"));
        assertThat(Delegataires.suggerer(comptes, "al", "u4")).isEmpty();
        assertThat(Delegataires.suggerer(comptes, null, "u4")).isEmpty();
        assertThat(Delegataires.suggerer(comptes, " ALEX ", "u4")).extracting(Delegataires.Suggestion::id).containsExactly("u1", "u3");
        assertThat(Delegataires.suggerer(comptes, "bob", "u4")).isEmpty();
        assertThat(Delegataires.suggerer(comptes, "chantal", "u4").getFirst())
                .isEqualTo(new Delegataires.Suggestion("u3", "Chantal Roy", "manager", "c@alexandrie.ca", "actif"));
    }
}

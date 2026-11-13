package ca.vina.nexu.communication.domaine;

import ca.vina.nexu.communication.domaine.ListesDistribution.MembreDemande;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Listes de distribution et brouillons — règles pures")
class ListesDistributionTest {

    @Test
    @DisplayName("validation : nom requis et borné, nature et cible de chaque membre")
    void validation() {
        assertThat(ListesDistribution.valider(null, List.of())).containsExactly("nom requis");
        assertThat(ListesDistribution.valider("x".repeat(121), List.of())).containsExactly("nom trop long (120 caractères au maximum)");
        assertThat(ListesDistribution.valider("Garde", List.of(new MembreDemande("service", " "), new MembreDemande(null, null))))
                .containsExactly("membre 1 : nature attendue parmi personne, groupe", "membre 1 : cible requise",
                        "membre 2 : nature attendue parmi personne, groupe", "membre 2 : cible requise");
        assertThat(ListesDistribution.valider(" Garde ", List.of(new MembreDemande("groupe", "g1")))).isEmpty();
    }

    @Test
    @DisplayName("destinataires : union triée, groupes dépliés, vides écartés")
    void destinataires() {
        List<ListesDistribution.Membre> m = ListesDistribution.membres(List.of(new MembreDemande("personne", " zed "), new MembreDemande("groupe", "g1")));
        assertThat(m.get(0).cible()).isEqualTo("zed");
        assertThat(ListesDistribution.destinataires(m, g -> java.util.Arrays.asList("bob", "zed", "", null))).containsExactly("bob", "zed");
    }

    @Test
    @DisplayName("visibilité et carte servie")
    void visibiliteEtCarte() {
        ListesDistribution.Liste privee = new ListesDistribution.Liste("l1", "Nuit", false, "roy", "t", List.of());
        assertThat(privee.visiblePar("roy")).isTrue();
        assertThat(privee.visiblePar("ngu")).isFalse();
        ListesDistribution.Liste partagee = new ListesDistribution.Liste("l1", "Nuit", true, "roy", "t", List.of())
                .avecMembres(List.of(new ListesDistribution.Membre(ListesDistribution.Nature.GROUPE, "g1")));
        assertThat(partagee.visiblePar("ngu")).isTrue();
        Map<String, Object> carte = partagee.enCarte();
        assertThat(carte.keySet()).containsExactly("id", "nom", "partagee", "creeePar", "creeeLe", "membres");
        assertThat(carte.get("membres")).isEqualTo(List.of(Map.of("nature", "groupe", "cible", "g1")));
        assertThat(ListesDistribution.Nature.de("x")).isEmpty();
    }

    @Test
    @DisplayName("brouillon : vide licite, absent ou trop long refusé")
    void brouillon() {
        assertThat(BrouillonsMessages.valider("")).isEmpty();
        assertThat(BrouillonsMessages.valider(null)).containsExactly("corps requis");
        assertThat(BrouillonsMessages.valider("x".repeat(10_001))).containsExactly("brouillon trop long (10 000 caractères au maximum)");
        assertThat(new BrouillonsMessages.Brouillon("c1", "a", "t").enCarte().keySet()).containsExactly("conversationId", "corps", "modifieLe");
    }
}

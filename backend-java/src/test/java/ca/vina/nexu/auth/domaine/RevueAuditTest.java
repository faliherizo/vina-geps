package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Revue des registres — validation, échéance à 90 jours, chaîne d'empreintes")
class RevueAuditTest {

    @Test
    void uneDemandeValideDonneSesBornes() {
        RevueAudit.Demande d = RevueAudit.valider("2026-06-01", "2026-06-30", "  Tout vérifié, rien d'anormal.  ");
        assertThat(d.debutIso()).isEqualTo("2026-06-01T00:00:00.000Z");
        assertThat(d.finIso()).isEqualTo("2026-06-30T23:59:59.999Z");
        assertThat(d.constat()).isEqualTo("Tout vérifié, rien d'anormal.");
    }

    @Test
    void lesErreursSontJointesMotPourMot() {
        assertThatThrownBy(() -> RevueAudit.valider(null, "30-06-2026", "court"))
                .hasMessage("du : date AAAA-MM-JJ attendue; au : date AAAA-MM-JJ attendue; constat requis (10 caractères au moins) : ce qui a été vérifié et ce qui a été trouvé");
        assertThatThrownBy(() -> RevueAudit.valider("2026-06-30", "2026-06-01", "Constat suffisant ici"))
                .hasMessage("la fin de période précède son début");
        assertThatThrownBy(() -> RevueAudit.valider("2026-06-01", "2026-06-30", null)).hasMessageStartingWith("constat requis");
    }

    @Test
    void lEcheanceSeCalculeDepuisLaDerniereRevueOuLaPremiereEntree() {
        Instant maintenant = Instant.parse("2026-09-13T12:00:00Z");
        Map<String, Object> aucune = new LinkedHashMap<>();
        aucune.put("derniere", null);
        aucune.put("prochaineAvant", null);
        aucune.put("enRetard", false);
        assertThat(RevueAudit.echeance(null, null, maintenant)).isEqualTo(aucune);
        Map<String, Object> depuisEntree = RevueAudit.echeance(null, "2026-05-01T08:00:00.000Z", maintenant);
        assertThat(depuisEntree.get("prochaineAvant")).isEqualTo("2026-07-30T08:00:00.000Z");
        assertThat(depuisEntree.get("enRetard")).isEqualTo(true);
        Map<String, Object> depuisRevue = RevueAudit.echeance("2026-09-01T00:00:00Z", "2026-05-01T08:00:00.000Z", maintenant);
        assertThat(depuisRevue.get("derniere")).isEqualTo("2026-09-01T00:00:00Z");
        assertThat(depuisRevue.get("prochaineAvant")).isEqualTo("2026-11-30T00:00:00.000Z");
        assertThat(depuisRevue.get("enRetard")).isEqualTo(false);
        assertThat(RevueAudit.echeance("2026-09-01T00:00:00.250Z", null, maintenant).get("prochaineAvant")).isEqualTo("2026-11-30T00:00:00.250Z");
    }

    @Test
    void laChaineNommeLaPremiereRupture() {
        EvenementAudit a = new EvenementAudit("1", "t1", null, null, "x", null, null);
        EvenementAudit b = new EvenementAudit("2", "t2", null, null, "y", null, null);
        EvenementAudit c = new EvenementAudit("3", "t3", null, null, "z", null, null);
        BiFunction<String, EvenementAudit, String> empreinte = (p, e) -> (p == null ? "" : p) + "/" + e.id();
        Map<String, Object> intacte = RevueAudit.integrite(List.of(new RevueAudit.Ligne(a, null), new RevueAudit.Ligne(b, "/2"), new RevueAudit.Ligne(c, "/2/3")), empreinte);
        assertThat(intacte).containsEntry("entrees", 3).containsEntry("chainees", 2).containsEntry("nonChainees", 1)
                .containsEntry("intacte", true).containsEntry("premiereRupture", null).containsEntry("derniereEmpreinte", "/2/3");
        Map<String, Object> rompue = RevueAudit.integrite(List.of(new RevueAudit.Ligne(b, "/2"), new RevueAudit.Ligne(c, "faux"), new RevueAudit.Ligne(a, "faux/1")), empreinte);
        assertThat(rompue.get("intacte")).isEqualTo(false);
        assertThat(rompue.get("premiereRupture")).isEqualTo(Map.of("id", "3", "at", "t3"));
        assertThat(RevueAudit.integrite(List.of(), empreinte)).containsEntry("entrees", 0).containsEntry("derniereEmpreinte", null);
    }
}

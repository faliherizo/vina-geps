package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Identités externes (ADR 0093) — clé (émetteur, sujet), autorisation, indice masqué, demandes de liaison")
class IdentitesExternesTest {

    private static Compte compte(String tenantId, boolean actif) {
        return new Compte("u1", "jean", "jean", tenantId, "h", "employee", null, "x", actif, false, false, "jean@x.ca", null, null, null);
    }

    @Test
    @DisplayName("la clé exige un émetteur et un sujet textuels, non vides, bornés ; ils sont nettoyés")
    void cle() {
        IdentitesExternes.Cle c = IdentitesExternes.Cle.de(" https://idp ", " s1 ");
        assertThat(c.emetteur().valeur()).isEqualTo("https://idp");
        assertThat(c.sujet().valeur()).isEqualTo("s1");
        assertThat(c).isEqualTo(IdentitesExternes.Cle.de("https://idp", "s1"));
        assertThatThrownBy(() -> IdentitesExternes.Cle.de("https://idp", null)).isInstanceOf(IdentitesExternes.IdentiteInvalideException.class)
                .hasMessage("le fournisseur n'a pas transmis de sujet utilisable (255 caractères au plus)");
        assertThatThrownBy(() -> IdentitesExternes.Cle.de(42, "s")).hasMessageContaining("émetteur");
        assertThatThrownBy(() -> new IdentitesExternes.Sujet("x".repeat(256))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentitesExternes.Emetteur(null)).hasMessageContaining("512");
        assertThat(new IdentitesExternes.Emetteur("e".repeat(512)).valeur()).hasSize(512);
    }

    @Test
    @DisplayName("l'autorisation : compte actif, et de l'organisme de l'annuaire ; le fournisseur d'instance n'impose pas d'organisme")
    void autoriser() {
        IdentitesExternes.Provenance t1 = IdentitesExternes.Provenance.annuaire("ann-1", "T1");
        IdentitesExternes.autoriser(compte("T1", true), t1);
        IdentitesExternes.autoriser(compte("T2", true), IdentitesExternes.Provenance.instance());
        IdentitesExternes.autoriser(compte(null, true), IdentitesExternes.Provenance.instance());
        assertThatThrownBy(() -> IdentitesExternes.autoriser(compte("T1", false), t1)).isInstanceOf(IdentitesExternes.LiaisonRefuseeException.class)
                .satisfies(e -> assertThat(((IdentitesExternes.LiaisonRefuseeException) e).motif()).isEqualTo(IdentitesExternes.Motif.COMPTE_INACTIF));
        assertThatThrownBy(() -> IdentitesExternes.autoriser(compte("T2", true), t1)).hasMessage("le compte n'appartient pas à l'organisme de cet annuaire");
        assertThatThrownBy(() -> IdentitesExternes.autoriser(compte(null, true), t1)).hasMessage(IdentitesExternes.Motif.ORGANISME_DIFFERENT.message());
        assertThat(IdentitesExternes.Provenance.instance().annuaireId()).isEmpty();
        assertThat(t1.tenantId()).contains("T1");
    }

    @Test
    @DisplayName("l'indice masque le courriel, et la preuve se lit par son code")
    void indiceEtPreuve() {
        assertThat(IdentitesExternes.masquer("jean.tremblay@Urgences-Sante.qc.ca")).isEqualTo("j***@urgences-sante.qc.ca");
        assertThat(IdentitesExternes.masquer("j@x.ca")).isEqualTo("***@x.ca");
        assertThat(IdentitesExternes.masquer("sans-arobase")).isEqualTo("***");
        for (IdentitesExternes.Preuve p : IdentitesExternes.Preuve.values()) {
            assertThat(IdentitesExternes.Preuve.de(p.code())).isEqualTo(p);
        }
        assertThatThrownBy(() -> IdentitesExternes.Preuve.de("courriel")).hasMessage("preuve de liaison inconnue : « courriel »");
    }

    @Test
    @DisplayName("une demande de liaison se décide une fois ; son statut se lit par son code")
    void demandes() {
        DemandesLiaison.Demande d = new DemandesLiaison.Demande("d1", IdentitesExternes.Cle.de("i", "s"), "a@x.ca", "T1", Optional.empty(), "u1",
                DemandesLiaison.Statut.EN_ATTENTE, Instant.EPOCH, Optional.empty(), Optional.empty());
        DemandesLiaison.Demande decidee = d.decider(DemandesLiaison.Statut.REFUSEE, Instant.EPOCH.plusSeconds(1), "adm");
        assertThat(decidee.statut()).isEqualTo(DemandesLiaison.Statut.REFUSEE);
        assertThat(decidee.decideePar()).contains("adm");
        assertThat(decidee.decideeLe()).contains(Instant.EPOCH.plusSeconds(1));
        assertThat(decidee.id()).isEqualTo("d1");
        for (DemandesLiaison.Statut s : DemandesLiaison.Statut.values()) {
            assertThat(DemandesLiaison.Statut.de(s.code())).isEqualTo(s);
        }
        assertThatThrownBy(() -> DemandesLiaison.Statut.de("x")).hasMessage("statut de demande inconnu : « x »");
    }
}

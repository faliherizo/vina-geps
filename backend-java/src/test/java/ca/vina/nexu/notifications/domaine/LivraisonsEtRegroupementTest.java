package ca.vina.nexu.notifications.domaine;

import ca.vina.nexu.partage.erreurs.domaine.VerrouException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Avis — journal des livraisons et regroupement")
class LivraisonsEtRegroupementTest {

    @Test
    @DisplayName("le journal masque adresses et numéros, nomme l'état, borne la limite et résume par état")
    void journal() {
        assertThat(Livraisons.expurger(null)).isNull();
        assertThat(Livraisons.expurger("")).isNull();
        assertThat(Livraisons.expurger("refus de julie@ex.ca au 514-555-1234")).isEqualTo("refus de (adresse masquée) au (numéro masqué)");
        Livraisons.Ligne l = Livraisons.ligne(new Livraisons.Envoi("n1", "hs.offerte", "email", "employee", "e1", "S", "C", "failed", "x@y.zz", 2, "c", null));
        assertThat(l.libelleEtat()).isEqualTo("échoué");
        assertThat(l.erreur()).isEqualTo("(adresse masquée)");
        assertThat(Livraisons.Etat.libelleDe("inconnu")).isEqualTo("inconnu");
        assertThat(Livraisons.limite(null)).isEqualTo(200);
        assertThat(Livraisons.limite(0)).isEqualTo(200);
        assertThat(Livraisons.limite(-5)).isEqualTo(1);
        assertThat(Livraisons.limite(5000)).isEqualTo(1000);
        assertThat(Livraisons.resumer(List.of(l, l)).parEtat()).containsEntry("échoué", 2);
    }

    @Test
    @DisplayName("seul un échec se relance : parti, canal fermé ou en attente sont refusés avec leur raison")
    void relance() {
        assertThatCode(() -> Livraisons.verifierRelance("failed")).doesNotThrowAnyException();
        assertThatThrownBy(() -> Livraisons.verifierRelance("sent")).isInstanceOf(VerrouException.class).hasMessageContaining("arriver deux fois");
        assertThatThrownBy(() -> Livraisons.verifierRelance("desactive")).hasMessageContaining("passerait outre son choix");
        assertThatThrownBy(() -> Livraisons.verifierRelance("queued")).hasMessageContaining("attendre son issue");
    }

    @Test
    @DisplayName("regroupement : mode effectif, groupé ou par objet, détail tronqué au-delà de douze lignes, compte")
    void regroupement() {
        assertThat(Regroupement.effectif(null, null)).isEqualTo(Regroupement.Mode.UN_AVIS_GROUPE);
        assertThat(Regroupement.effectif(null, Regroupement.Mode.UN_AVIS_PAR_OBJET)).isEqualTo(Regroupement.Mode.UN_AVIS_PAR_OBJET);
        assertThat(Regroupement.effectif(Regroupement.Mode.UN_AVIS_GROUPE, Regroupement.Mode.UN_AVIS_PAR_OBJET)).isEqualTo(Regroupement.Mode.UN_AVIS_GROUPE);
        assertThatThrownBy(() -> Regroupement.Mode.de("tout")).hasMessageStartingWith("mode de regroupement inconnu « tout »");
        assertThat(Regroupement.Mode.de(" un_avis_par_objet ")).isEqualTo(Regroupement.Mode.UN_AVIS_PAR_OBJET);
        List<Regroupement.ObjetAvis> objets = new java.util.ArrayList<>();
        for (int i = 0; i < 13; i++) {
            objets.add(new Regroupement.ObjetAvis("a", "quart " + i));
        }
        objets.add(new Regroupement.ObjetAvis("b", "quart x"));
        objets.add(new Regroupement.ObjetAvis("b", "quart y"));
        List<Regroupement.MessageConsolide> m = Regroupement.consolider(objets,
                p -> "b".equals(p) ? Regroupement.Mode.UN_AVIS_PAR_OBJET : Regroupement.Mode.UN_AVIS_GROUPE, n -> n + " quart(s)");
        assertThat(m).hasSize(3);
        assertThat(m.getFirst().corps()).isEqualTo("13 quart(s)\n\nLe détail se lit dans l'application : la liste est trop longue pour un message.");
        assertThat(m.get(1).corps()).isEqualTo("1 quart(s)\n\nquart x");
        assertThat(Regroupement.compter(m)).isEqualTo(new Regroupement.Compte(3, 2, 15));
        assertThat(Livraisons.Etat.FAILED.code()).isEqualTo("failed");
        assertThat(Regroupement.consolider(List.of(new Regroupement.ObjetAvis("c", "q")), p -> Regroupement.Mode.UN_AVIS_GROUPE, n -> "E").getFirst().corps())
                .isEqualTo("E\n\nq");
    }
}

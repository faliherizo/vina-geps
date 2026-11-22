package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.OffresQuart.EtatRang;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OffresQuart — ordre par ancienneté et à tour de rôle, délais, état d'une offre (AD-07 ; art. 15.10, 16.02, 16.03)")
class OffresQuartTest {

    private static final String T = "2030-03-04T15:00:00Z";

    private static OffreQuart.Rang rang(int n, String id, EtatRang etat, String echeance) {
        return new OffreQuart.Rang(n, id, id, 0, 0, 12, etat, null, echeance, null, null);
    }

    private static OffreQuart offre(boolean urgente, OffresQuart.EtatOffre etat, OffreQuart.Rang... rangs) {
        return new OffreQuart("o1", "c1", "a1", new OffreQuart.Quart("p1", "EST", "2030-03-05", 360, 1080), urgente, etat, T, null, List.of(rangs));
    }

    @Test
    @DisplayName("tour de rôle : le moins d'heures comptées d'abord (16.02, 16.03), l'ancienneté départage, puis l'identifiant")
    void ordre() {
        List<OffresQuart.Candidat> ordre = OffresQuart.ordonner(List.of(new OffresQuart.Candidat("lea", "Léa", 15, 11.25),
                new OffresQuart.Candidat("theo", "Théo", 6, 0), new OffresQuart.Candidat("marc", "Marc", 10, 0), new OffresQuart.Candidat("ana", "Ana", 10, 0)));
        assertThat(ordre).extracting(OffresQuart.Candidat::ressourceId).containsExactly("ana", "marc", "theo", "lea");
        assertThat(OffresQuart.heuresDuQuart(360, 1080)).isEqualTo(12.0);
        assertThat(OffresQuart.heuresDuQuart(1140, 375)).isEqualTo(11.25);
    }

    @Test
    @DisplayName("délais : dix minutes ; un quart qui commence dans le seuil d'urgence vaut jusqu'à son début, ou le délai s'il a commencé")
    void delais() {
        String debut = "2030-03-04T17:00:00Z";
        assertThat(OffresQuart.estUrgente(T, debut, 120)).isTrue();
        assertThat(OffresQuart.estUrgente(T, debut, 119)).isFalse();
        assertThat(OffresQuart.echeance(T, debut, false, 10)).isEqualTo("2030-03-04T15:10:00.000Z");
        assertThat(OffresQuart.echeance(T, debut, true, 10)).isEqualTo("2030-03-04T17:00:00.000Z");
        assertThat(OffresQuart.echeance(T, "2030-03-04T14:00:00Z", true, 10)).isEqualTo("2030-03-04T15:10:00.000Z");
        assertThat(OffresQuart.debutFenetre(T, 91)).isEqualTo("2029-12-03T15:00:00.000Z");
        assertThat(OffresQuart.heureLocale("2030-03-04T15:10:00.000Z")).isEqualTo("2030-03-04 à 10:10");
    }

    @Test
    @DisplayName("réglage : défauts de la convention en mode simultané ; un champ absent se garde, hors borne ou hors ensemble il est refusé en le nommant")
    void parametres() {
        OffresQuart.Parametres p = OffresQuart.Parametres.PAR_DEFAUT;
        assertThat(p).isEqualTo(new OffresQuart.Parametres(OffresQuart.Mode.SIMULTANEE, 10, 120, 91));
        assertThat(p.estEnCascade()).isFalse();
        OffresQuart.Parametres c = p.modifiesPar(Map.of("mode", "CASCADE", "delaiReponseMinutes", 15));
        assertThat(c).isEqualTo(new OffresQuart.Parametres(OffresQuart.Mode.CASCADE, 15, 120, 91));
        assertThat(c.estEnCascade()).isTrue();
        assertThat(c.modifiesPar(Map.of("seuilUrgenceMinutes", 0, "fenetreRepartitionJours", 366))).isEqualTo(new OffresQuart.Parametres(OffresQuart.Mode.CASCADE, 15, 0, 366));
        assertThatThrownBy(() -> p.modifiesPar(Map.of("mode", "cascade"))).isInstanceOf(EffectifsInvalideException.class).hasMessage("« mode » : SIMULTANEE, CASCADE");
        assertThatThrownBy(() -> p.modifiesPar(Map.of("delaiReponseMinutes", 0))).hasMessage("« delaiReponseMinutes » : un nombre entier de 1 à 1440 est attendu");
        assertThatThrownBy(() -> p.modifiesPar(Map.of("delaiReponseMinutes", 1441))).hasMessage("« delaiReponseMinutes » : un nombre entier de 1 à 1440 est attendu");
        assertThatThrownBy(() -> p.modifiesPar(Map.of("delaiReponseMinutes", 9.5))).isInstanceOf(EffectifsInvalideException.class);
        assertThatThrownBy(() -> p.modifiesPar(Map.of("seuilUrgenceMinutes", "120"))).hasMessage("« seuilUrgenceMinutes » : un nombre entier de 0 à 1440 est attendu");
        Map<String, Object> nul = new HashMap<>();
        nul.put("fenetreRepartitionJours", null);
        assertThatThrownBy(() -> p.modifiesPar(nul)).hasMessage("« fenetreRepartitionJours » : un nombre entier de 1 à 366 est attendu");
    }

    @Test
    @DisplayName("états : seuls l'acceptation, le refus et le silence comptent ; chaque état hors de l'offre dit pourquoi ; les événements ont leur code")
    void etats() {
        assertThat(EtatRang.comptes()).containsExactly(EtatRang.ACCEPTE, EtatRang.REFUSE, EtatRang.EXPIRE);
        assertThat(EtatRang.EN_ATTENTE.pourquoiPasOffert()).isEqualTo("ce n'est pas encore votre tour");
        assertThat(EtatRang.EXPIRE.pourquoiPasOffert()).startsWith("le délai de réponse est échu");
        assertThat(OffresQuart.Evenement.ACCEPTEE.code()).isEqualTo("offre.acceptee");
        assertThat(OffresQuart.Evenement.PARAMETRES.code()).isEqualTo("offres.parametres");
    }

    @Test
    @DisplayName("une offre en cascade avise le rang suivant ; urgente, tous les rangs une seule fois ; une proposition échoit à son échéance")
    void offre() {
        OffreQuart cascade = offre(false, OffresQuart.EtatOffre.OUVERTE, rang(1, "marc", EtatRang.REFUSE, null), rang(2, "theo", EtatRang.OFFERT,
                "2030-03-04T15:10:00.000Z"), rang(3, "lea", EtatRang.EN_ATTENTE, null));
        assertThat(cascade.estOuverte()).isTrue();
        assertThat(cascade.aUnRangOffert()).isTrue();
        assertThat(cascade.echus("2030-03-04T15:09:59Z")).isEmpty();
        assertThat(cascade.echus("2030-03-04T15:10:00Z")).extracting(OffreQuart.Rang::ressourceId).containsExactly("theo");
        assertThat(cascade.suivant()).map(OffreQuart.Rang::ressourceId).contains("lea");
        assertThat(cascade.aAviser()).extracting(OffreQuart.Rang::ressourceId).containsExactly("lea");
        assertThat(cascade.rangs().get(0).estEchu(T)).isFalse();

        OffreQuart urgente = offre(true, OffresQuart.EtatOffre.OUVERTE, rang(1, "marc", EtatRang.EN_ATTENTE, null), rang(2, "lea", EtatRang.EN_ATTENTE, null));
        assertThat(urgente.aAviser()).hasSize(2);
        assertThat(offre(true, OffresQuart.EtatOffre.OUVERTE, rang(1, "marc", EtatRang.REFUSE, null), rang(2, "lea", EtatRang.EN_ATTENTE, null)).aAviser()).isEmpty();
        assertThat(offre(false, OffresQuart.EtatOffre.EPUISEE).estOuverte()).isFalse();
    }

    @Test
    @DisplayName("répondre : seul le rang offert le peut ; hors de l'ordre, l'offre est introuvable ; dans l'ordre, la raison est dite")
    void exigerOffert() {
        OffreQuart o = offre(false, OffresQuart.EtatOffre.OUVERTE, rang(1, "marc", EtatRang.OFFERT, "2030-03-04T15:10:00Z"), rang(2, "lea", EtatRang.EN_ATTENTE, null));
        assertThat(o.exigerOffertA("marc").rang()).isEqualTo(1);
        assertThat(o.vuePour("marc").rangs()).extracting(OffreQuart.Rang::ressourceId).containsExactly("marc");
        assertThat(o.vuePour("marc").id()).isEqualTo("o1");
        assertThatThrownBy(() -> o.exigerOffertA("lea")).isInstanceOf(ConflitException.class).hasMessage("ce n'est pas encore votre tour");
        assertThatThrownBy(() -> o.exigerOffertA("julie")).isInstanceOf(IntrouvableException.class).hasMessage("offre de quart introuvable : o1");
    }
}

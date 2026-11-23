package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AD-06b (addenda 5 c, M-01) : les soldes reçus de BD Corpo, et les faits qu'ils donnent à une demande d'absence. */
@DisplayName("Soldes des banques : lecture d'un envoi, faits d'une demande d'absence")
class SoldesBanquesTest {

    private static Map<String, Object> s(Object banque, Object heures, Object au) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("banque", banque);
        m.put("heures", heures);
        m.put("au", au);
        return m;
    }

    @Test
    @DisplayName("un envoi valide : code mis en majuscules, heures à deux décimales, négatif admis")
    void lire() {
        List<SoldesBanques.Solde> l = SoldesBanques.lire("r-1", Map.of("soldes", List.of(s(" vac ", 70.456, "2026-11-20"), s("MAL", -3, " 2026-11-20 "))), "t0");
        assertThat(l).extracting(SoldesBanques.Solde::banqueCode).containsExactly("VAC", "MAL");
        assertThat(l.get(0).heures()).isEqualByComparingTo("70.46");
        assertThat(l.get(1).heures()).isEqualByComparingTo("-3.00");
        assertThat(l.get(1).auJour()).isEqualTo("2026-11-20");
        assertThat(l.get(0).recuLe()).isEqualTo("t0");
    }

    @Test
    @DisplayName("refus : liste absente, vide ou trop longue ; banque en double ; heures hors borne ; chaque écart nommé")
    void refus() {
        for (Object corps : new Object[] {null, Map.of(), Map.of("soldes", "x"), Map.of("soldes", List.of()),
                Map.of("soldes", Collections.nCopies(SoldesBanques.SOLDES_PAR_ENVOI + 1, s("A", 1, "2026-01-01")))}) {
            assertThatThrownBy(() -> SoldesBanques.lire("r", corps, "t")).isInstanceOf(EffectifsInvalideException.class)
                    .hasMessage("« soldes » : liste de 1 à 50 soldes attendue");
        }
        assertThatThrownBy(() -> SoldesBanques.lire("r", Map.of("soldes", List.of(s("VAC", 1, "2026-01-01"), s("vac", 2, "2026-01-02"))), "t"))
                .hasMessage("soldes[1].banque : « VAC » déjà présente dans cet envoi");
        assertThatThrownBy(() -> SoldesBanques.lire("r", Map.of("soldes", List.of(s("VAC", 100000, "2026-01-01"), "pas un objet")), "t"))
                .hasMessage("soldes[0].heures : nombre de -99999 à 99999 ; soldes[1].banque : code de 1 à 40 lettres, chiffres, tirets ou soulignés ; "
                        + "soldes[1].heures : nombre de -99999 à 99999 ; soldes[1].au : date AAAA-MM-JJ du solde");
        assertThatThrownBy(() -> SoldesBanques.lire("r", Map.of("soldes", List.of(s("V A", 1, 20260101))), "t"))
                .hasMessageContaining("soldes[0].banque").hasMessageContaining("soldes[0].au");
    }

    @Test
    @DisplayName("faits : solde connu, heures demandées = jours × heures par jour, solde après la demande")
    void faits() {
        SoldesBanques.Solde vac = new SoldesBanques.Solde("r", "VAC", new BigDecimal("21"), "2026-11-20", "t");
        SoldesBanques.FaitsSolde f = SoldesBanques.FaitsSolde.de(Optional.of(vac), 3, Optional.of(new BigDecimal("7.5")));
        assertThat(f.connu()).isTrue();
        assertThat(f.heuresDemandees()).isEqualByComparingTo("22.50");
        assertThat(f.soldeApresHeures()).isEqualByComparingTo("-1.50");
        SoldesBanques.FaitsSolde inconnu = SoldesBanques.FaitsSolde.de(Optional.empty(), 3, Optional.empty());
        assertThat(inconnu).isEqualTo(new SoldesBanques.FaitsSolde(false, new BigDecimal("0.00"), new BigDecimal("0.00"), BigDecimal.ZERO));
    }

    @Test
    @DisplayName("le code d'absence nomme sa banque et ses heures par jour ; sinon, rien")
    void attributsDuCode() {
        assertThat(SoldesBanques.FaitsSolde.banqueDe(Map.of("banque", " vac "))).contains("VAC");
        assertThat(SoldesBanques.FaitsSolde.banqueDe(Map.of("banque", " "))).isEmpty();
        assertThat(SoldesBanques.FaitsSolde.banqueDe(Map.of())).isEmpty();
        assertThat(SoldesBanques.FaitsSolde.heuresParJourDe(Map.of("heuresParJour", 7.25))).contains(new BigDecimal("7.25"));
        assertThat(SoldesBanques.FaitsSolde.heuresParJourDe(Map.of("heuresParJour", "7"))).isEmpty();
        assertThat(TypeReferentiel.CODES_ABSENCE.attributs()).extracting(DescripteurAttribut::nom).contains("banque", "heuresParJour");
        assertThat(ContexteRegle.ABSENCE.faits()).extracting(FaitRegle::code)
                .contains("soldeBanqueConnu", "soldeBanqueHeures", "heuresDemandees", "soldeBanqueApresHeures");
    }
}

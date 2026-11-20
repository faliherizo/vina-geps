package ca.vina.nexu.saas.domaine;

import ca.vina.nexu.partage.erreurs.domaine.RefusException;
import ca.vina.nexu.partage.erreurs.domaine.VerrouException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FX-AO-20260810, clause 10.18.04 b) iv) et annexe 10.18.04 C : l'attestation au gabarit du contrat, dans les 60 jours. */
@DisplayName("Attestation de destruction au gabarit de l'annexe 10.18.04 C")
class AnnexeDestructionTest {

    private static final AnnexeDestruction.Signataire SIGNATAIRE =
            new AnnexeDestruction.Signataire(" Claire Gagnon ", "VINA IT Solution", "1000, rue Sherbrooke Ouest, Montréal");
    private static final Destruction.Attestation ATTESTATION = new Destruction.Attestation("a-1", "t-9", "Santé Québec Laurentides",
            "2031-04-02T14:00:00Z", "superadmin", null, List.of(new Destruction.Table("employees", 3), new Destruction.Table("alertes", 4)), 7, "ab12");

    @Test
    @DisplayName("le texte suit l'annexe : signataire, fournisseur, adresse, fin du contrat, méthode logicielle cochée, signature")
    void texte() {
        LocalDate fin = LocalDate.of(2031, 3, 31);
        String t = AnnexeDestruction.texte(ATTESTATION, SIGNATAIRE, AnnexeDestruction.Echeance.de(fin, LocalDate.of(2031, 4, 3)), LocalDate.of(2031, 4, 3));
        assertThat(t).startsWith("ANNEXE 10.18.04 C - ATTESTATION DE DESTRUCTION DES RENSEIGNEMENTS")
                .contains("Date : 2031-04-03")
                .contains("Je, soussigné(e), Claire Gagnon, exerçant mes fonctions au sein de VINA IT Solution, dont le bureau principal est situé à l'adresse 1000, rue Sherbrooke Ouest, Montréal")
                .contains("l'ORGANISME PUBLIC (Santé Québec Laurentides)")
                .contains("à VINA IT Solution et qui prend fin le 2031-03-31")
                .contains("☐ par déchiquetage", "☒ par destruction logique et effacement physique en utilisant un logiciel de réécriture", "☐ par un autre mode")
                .contains("Claire Gagnon (signature de l'employé)")
                .contains("7 lignes détruites dans 2 table(s) ; empreinte SHA-256 ab12")
                .contains("au plus tard le 2031-05-30 (clause 10.18.04 b iv, 60 jours suivant la fin du contrat)")
                .doesNotContain("DÉLAI DÉPASSÉ");
    }

    @Test
    @DisplayName("l'échéance : fin du contrat + 60 jours ; au-delà, l'attestation le dit")
    void echeance() {
        LocalDate fin = LocalDate.of(2031, 3, 31);
        assertThat(AnnexeDestruction.Echeance.de(fin, LocalDate.of(2031, 5, 30)))
                .isEqualTo(new AnnexeDestruction.Echeance(fin, LocalDate.of(2031, 5, 30), false));
        AnnexeDestruction.Echeance tard = AnnexeDestruction.Echeance.de(fin, LocalDate.of(2031, 5, 31));
        assertThat(tard.horsDelai()).isTrue();
        assertThat(AnnexeDestruction.texte(ATTESTATION, SIGNATAIRE, tard, LocalDate.of(2031, 5, 31))).contains("— DÉLAI DÉPASSÉ");
    }

    @Test
    @DisplayName("jamais signée à blanc : chaque donnée manquante du signataire est nommée par sa variable")
    void signataireIncomplet() {
        assertThatThrownBy(() -> new AnnexeDestruction.Signataire(null, " ", null).exigerComplete()).isInstanceOf(VerrouException.class)
                .hasMessage("signataire autorisé non configuré (annexe 10.18.04 C) : SAAS_ATTESTATION_SIGNATAIRE, SAAS_ATTESTATION_FOURNISSEUR, SAAS_ATTESTATION_ADRESSE");
        assertThatThrownBy(() -> new AnnexeDestruction.Signataire("C", "", "A").exigerComplete()).hasMessageEndingWith(": SAAS_ATTESTATION_FOURNISSEUR");
        assertThatThrownBy(() -> new AnnexeDestruction.Signataire("C", "F", "").exigerComplete()).hasMessageEndingWith(": SAAS_ATTESTATION_ADRESSE");
        assertThatThrownBy(() -> AnnexeDestruction.texte(ATTESTATION, new AnnexeDestruction.Signataire("", "F", "A"),
                AnnexeDestruction.Echeance.de(LocalDate.of(2031, 3, 31), LocalDate.of(2031, 4, 1)), LocalDate.of(2031, 4, 1)))
                .hasMessageEndingWith(": SAAS_ATTESTATION_SIGNATAIRE");
        assertThat(SIGNATAIRE.nom()).isEqualTo("Claire Gagnon");
    }

    @Test
    @DisplayName("la fin du contrat est une date AAAA-MM-JJ, convertie une fois à la frontière")
    void finContrat() {
        assertThat(AnnexeDestruction.finContrat(" 2031-03-31 ")).isEqualTo(LocalDate.of(2031, 3, 31));
        for (String absente : new String[] {null, " "}) {
            assertThatThrownBy(() -> AnnexeDestruction.finContrat(absente)).isInstanceOf(RefusException.class)
                    .satisfies(e -> assertThat(((RefusException) e).erreurs()).containsExactly("finContrat : date de fin du contrat attendue (AAAA-MM-JJ)"));
        }
        assertThatThrownBy(() -> AnnexeDestruction.finContrat("31/03/2031")).isInstanceOf(RefusException.class)
                .satisfies(e -> assertThat(((RefusException) e).erreurs()).containsExactly("finContrat : « 31/03/2031 » n'est pas une date AAAA-MM-JJ"));
    }
}

package ca.vina.nexu.notifications.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le découpage facturé d'un texto — jumeau de {@code backend/tests/unit/segments-sms.test.ts}.
 *
 * <p>Ces essais portent sur de l'argent : un segment mal compté, ce sont des dépassements
 * facturés en moins (ou en trop). Les deux piles doivent rendre le <b>même</b> chiffre, sans quoi
 * la bascule de l'une à l'autre changerait la facture d'un organisme.
 */
@DisplayName("Segments — ce que le fournisseur facture, et le piège des accents français")
class SegmentsTest {

    @Test
    @DisplayName("une partie seulement des accents français tient dans l'alphabet des textos")
    void encodage() {
        assertThat(Segments.decouper("Le remplacement est confirmé à 8 h ; où déjà ?").encodage()).isEqualTo(Segments.Encodage.GSM7);
        assertThat(Segments.decouper("ECOLE FERMEE - Ç É Ä Ö Ñ Ü").encodage()).isEqualTo(Segments.Encodage.GSM7);
        assertThat(Segments.decouper("Ligne 1\nLigne 2").encodage()).isEqualTo(Segments.Encodage.GSM7);
    }

    @Test
    @DisplayName("« ç » minuscule et « À » majuscule n'y sont pas — l'asymétrie est contre-intuitive")
    void pieges() {
        // La table GSM 03.38 contient Ç mais pas ç, et à mais pas À. Un seul caractère, invisible
        // à la relecture, double le coût du message.
        assertThat(Segments.decouper("Ça commence à 8 h").encodage()).isEqualTo(Segments.Encodage.GSM7);
        assertThat(Segments.decouper("ça commence à 8 h").encodage()).isEqualTo(Segments.Encodage.UCS2);
        assertThat(Segments.decouper("ça commence à 8 h").caractereDeclencheur()).isEqualTo("ç");
        assertThat(Segments.decouper("À compter de lundi").caractereDeclencheur()).isEqualTo("À");
        for (String mot : new String[] {"être", "coût", "août", "hôpital", "bientôt", "maître", "île"}) {
            assertThat(Segments.decouper(mot).encodage()).as(mot).isEqualTo(Segments.Encodage.UCS2);
        }
    }

    @Test
    @DisplayName("nomme le premier caractère fautif — « UCS-2 » n'aide personne, « le ô de bientôt » se corrige")
    void declencheur() {
        assertThat(Segments.decouper("a bientôt 😀").caractereDeclencheur()).isEqualTo("ô");
        assertThat(Segments.decouper("Rendez-vous 🙂").caractereDeclencheur()).isEqualTo("🙂");
        assertThat(Segments.decouper("Bonjour").caractereDeclencheur()).isNull();
        assertThat(Segments.Encodage.GSM7.code()).isEqualTo("gsm7");
        assertThat(Segments.Encodage.UCS2.code()).isEqualTo("ucs2");
    }

    @Test
    @DisplayName("GSM-7 : 160 pour un seul segment, puis 153 — l'en-tête de réassemblage coûte 7 octets")
    void bornesGsm7() {
        assertThat(Segments.compter("a".repeat(160))).isEqualTo(1);
        assertThat(Segments.compter("a".repeat(161))).isEqualTo(2);
        assertThat(Segments.compter("a".repeat(306))).isEqualTo(2);
        assertThat(Segments.compter("a".repeat(307))).isEqualTo(3);
    }

    @Test
    @DisplayName("UCS-2 : 70 pour un seul segment, puis 67")
    void bornesUcs2() {
        assertThat(Segments.compter("ê" + "a".repeat(69))).isEqualTo(1);
        assertThat(Segments.compter("ê" + "a".repeat(70))).isEqualTo(2);
        assertThat(Segments.compter("ê" + "a".repeat(133))).isEqualTo(2);
        assertThat(Segments.compter("ê" + "a".repeat(134))).isEqualTo(3);
    }

    @Test
    @DisplayName("le même avis coûte huit segments ou quatre selon un seul accent")
    void demonstrationDuDefaut() {
        // La démonstration chiffrée : LONGUEUR_MAX vaut 480, et le journal comptait « 1 texto »
        // dans les deux cas.
        String sans = "Vous devez etre present demain. ".repeat(15).substring(0, 480);
        String avec = "Vous devez être present demain. ".repeat(15).substring(0, 480);
        assertThat(Segments.compter(sans)).isEqualTo(4);
        assertThat(Segments.compter(avec)).isEqualTo(8);
    }

    @Test
    @DisplayName("les caractères de la table d'extension comptent double — l'euro surprend")
    void extension() {
        assertThat(Segments.decouper("€").encodage()).isEqualTo(Segments.Encodage.GSM7);
        assertThat(Segments.decouper("€").unites()).isEqualTo(2);
        assertThat(Segments.compter("€".repeat(80))).isEqualTo(1);
        assertThat(Segments.compter("€".repeat(81))).isEqualTo(2);
        for (String c : new String[] {"^", "{", "}", "\\", "[", "~", "]", "|"}) {
            assertThat(Segments.decouper(c).unites()).as(c).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("un émoji hors du plan de base occupe deux unités ; un texte vide ou absent vaut un segment")
    void bornes() {
        assertThat(Segments.decouper("😀").unites()).isEqualTo(2);
        assertThat(Segments.decouper("😀".repeat(35)).segments()).isEqualTo(1);
        assertThat(Segments.decouper("😀".repeat(36)).segments()).isEqualTo(2);
        // Le fournisseur facture la tentative : zéro segment serait une porte ouverte au quota.
        assertThat(Segments.compter("")).isEqualTo(1);
        assertThat(Segments.compter(null)).isEqualTo(1);
        assertThat(Segments.decouper(null).unites()).isZero();
    }
}

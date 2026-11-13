package ca.vina.nexu.auth.domaine;

import ca.vina.nexu.partage.crypto.Primitives;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le code QR est comparé au Node <strong>octet pour octet</strong> (empreinte SHA-256 du SVG produit
 * par {@code qrcode.ts} pour les mêmes charges) : une matrice différente serait un code qu'une
 * application d'authentification lirait autrement — ou pas du tout.
 */
@DisplayName("Code QR — port fidèle du générateur Node (mode octet, niveau M, versions 1 à 6)")
class CodeQrTest {

    private static String sha(String s) {
        return HexFormat.of().formatHex(Primitives.sha256(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("les mêmes charges donnent les mêmes SVG que le Node, à l'octet près")
    void identiqueAuNode() {
        String otp = "otpauth://totp/NexuVINA:admin?secret=JBSWY3DPEHPK3PXP&issuer=NexuVINA";
        boolean[][] m = CodeQr.matrice(otp);
        assertThat(m).hasDimensions(37, 37);   // version 4
        assertThat(CodeQr.sombres(m)).isEqualTo(688);
        String svg = CodeQr.svg(otp, 4, 4);
        assertThat(svg).startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"180\" height=\"180\" viewBox=\"0 0 180 180\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code TOTP\">"
                + "<rect width=\"180\" height=\"180\" fill=\"#ffffff\"/><g fill=\"#000000\"><rect x=\"16\" y=\"16\" width=\"4\" height=\"4\"/>");
        assertThat(svg).hasSize(29723);
        assertThat(sha(svg)).isEqualTo("fb986b996b02c1a63b9b6443f79ebf4104cd953b49948fc0e09b0f58e3e6aa35");

        assertThat(CodeQr.matrice("A")).hasDimensions(21, 21);   // version 1
        assertThat(CodeQr.sombres(CodeQr.matrice("A"))).isEqualTo(212);
        assertThat(sha(CodeQr.svg("A", 4, 4))).isEqualTo("6a45ecb5bc062f50a167d9f15613dcb4ccf4a2c9b6d890f687892fde8f0fd57f");

        // Version 6, la plus grande admise ; et de l'UTF-8 sur plusieurs octets.
        String compacte = Totp.urlOtpauthCompacte("josee.tremblay@cssdc-demo.qc.ca@@T1", "GEZDGNBVGY3TQOJQ", "NexuVINA");
        assertThat(compacte).isEqualTo("otpauth://totp/NexuVINA:josee.tremblay%40cssdc-demo.qc.ca%40?secret=GEZDGNBVGY3TQOJQ&issuer=NexuVINA");
        assertThat(CodeQr.matrice(compacte)).hasDimensions(41, 41);
        assertThat(CodeQr.sombres(CodeQr.matrice(compacte))).isEqualTo(823);
        assertThat(sha(CodeQr.svg(compacte, 2, 1))).isEqualTo("934a7527d376c19d2d1207868bfa208d52c62d89347c9415ec0b5cd9ce5e5e53");
        String accents = "Nexu VINA — démonstration (é)!";
        assertThat(CodeQr.matrice(accents)).hasDimensions(29, 29);
        assertThat(CodeQr.sombres(CodeQr.matrice(accents))).isEqualTo(426);
        assertThat(sha(CodeQr.svg(accents, 2, 1))).isEqualTo("753311bf10a80d6a0c4e02675c2c12f4e5de8b48fff7132694ce9cb1dd4a24b5");
    }

    @Test
    @DisplayName("au-delà de la version 6, la charge est refusée plutôt que rendue illisible")
    void tropLong() {
        assertThatThrownBy(() -> CodeQr.matrice("x".repeat(107))).isInstanceOf(IllegalArgumentException.class).hasMessage("payload too long for QR (max version 6)");
        assertThat(CodeQr.matrice("x".repeat(106))).hasDimensions(41, 41);
        assertThatThrownBy(() -> CodeQr.masque(8, 0, 0)).hasMessage("bad mask:8");
        assertThat(CodeQr.masque(5, 0, 0)).isTrue();
        assertThat(CodeQr.masque(7, 1, 2)).isFalse();
        assertThat(CodeQr.formatBch(0)).isEqualTo(0b101010000010010);
    }
}

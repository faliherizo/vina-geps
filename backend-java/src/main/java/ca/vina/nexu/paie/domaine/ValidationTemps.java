package ca.vina.nexu.paie.domaine;

import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleAPayer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * Ce que la paie a décidé d'une feuille approuvée (ADR 0084), avec l'<strong>empreinte</strong> des heures qu'elle a
 * vues. Une feuille rouverte, corrigée puis réapprouvée change d'empreinte : la validation ne la couvre plus, et la
 * feuille ne se paie pas sans un nouveau regard.
 */
public record ValidationTemps(String id, String feuilleId, Decision decision, String empreinte, String motif, String acteur, String instant) {

    public enum Decision {
        VALIDEE("validee"), RETOURNEE("retournee");

        private final String code;

        Decision(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Decision de(String code) {
            return Arrays.stream(values()).filter(d -> d.code.equals(code)).findFirst()
                    .orElseThrow(() -> RefusPaie.invalide("décision de validation inconnue : " + code));
        }
    }

    /** La validation couvre-t-elle ces heures-là, telles qu'elles sont maintenant ? */
    public boolean couvre(FeuilleAPayer heures) {
        return decision == Decision.VALIDEE && empreinte.equals(empreinteDe(heures));
    }

    /** SHA-256 des lignes de paie de la feuille (date, code, nature, heures), dans l'ordre où les effectifs les rendent. */
    public static String empreinteDe(FeuilleAPayer heures) {
        String texte = heures.lignes().stream().map(l -> l.date() + ";" + l.codePaie() + ";" + l.nature() + ";" + l.heures().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("\n"));
        return HexFormat.of().formatHex(Primitives.sha256(texte.getBytes(StandardCharsets.UTF_8)));
    }
}

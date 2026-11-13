package ca.vina.nexu.partage.journal;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/** Les quatre niveaux du journal (ADR 0041), du plus bavard au plus rare — l'ordre est le seuil. */
public enum Niveau {
    DEBUG("debug"), INFO("info"), WARN("warn"), ERROR("error");

    private final String code;

    Niveau(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<Niveau> de(String code) {
        String v = code == null ? "" : code.trim().toLowerCase();
        return Arrays.stream(values()).filter(n -> n.code.equals(v)).findFirst();
    }

    /** `LOG_NIVEAU` tel que le yml le pose : vide = `info` ; inconnu = refusé, nommé (le démarrage s'arrête). */
    public static Niveau lu(String brut) {
        if (brut == null || brut.isBlank()) {
            return INFO;
        }
        return de(brut).orElseThrow(() -> new IllegalArgumentException("LOG_NIVEAU « " + brut + " » inconnu : " + codes()));
    }

    public static String codes() {
        return Arrays.stream(values()).map(Niveau::code).collect(Collectors.joining(", "));
    }
}

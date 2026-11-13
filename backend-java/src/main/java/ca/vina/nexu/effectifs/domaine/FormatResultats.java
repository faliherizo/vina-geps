package ca.vina.nexu.effectifs.domaine;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Les formats dans lesquels un résultat de recherche des effectifs s'emporte — P-TN-8 : « exporter
 * les résultats de recherche sur demande dans ces différents formats : Excel, PDF, CSV, JSON ».
 * Le code est celui du paramètre {@code format} de la requête ; son absence vaut {@link #JSON},
 * la réponse ordinaire de l'écran.
 */
public enum FormatResultats {
    JSON("json"), CSV("csv"), XLSX("xlsx"), PDF("pdf");

    private final String code;

    FormatResultats(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Converti une fois, à la frontière : un format inconnu est refusé en nommant ceux qui existent. */
    public static FormatResultats de(String code) {
        if (code == null || code.isEmpty()) {
            return JSON;
        }
        return Arrays.stream(values()).filter(f -> f.code.equals(code)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("format inconnu : « " + code + " » (attendu : "
                        + Arrays.stream(values()).map(FormatResultats::code).collect(Collectors.joining(", ")) + ")"));
    }
}

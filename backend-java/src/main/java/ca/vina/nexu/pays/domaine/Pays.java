package ca.vina.nexu.pays.domaine;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Le catalogue des pays hôtes et ce qu'une requête doit fournir — messages figés par le contrat du Node. */
public final class Pays {

    private Pays() {
    }

    /** Port : les packs de pays, tels que la configuration les décrit. */
    public interface Catalogue {
        String paysParDefaut();

        List<PaysPack> tous();

        Optional<PaysPack> pack(String code);

        /** Le pack tel qu'il figure dans les données d'origine ({@code publicView} du Node) — clés absentes et nulles distinguées. */
        Map<String, Object> vuePublique(String code);
    }

    public static PaysPack exiger(Catalogue catalogue, String code) {
        return catalogue.pack(code == null ? "" : code.toUpperCase()).orElseThrow(() -> new IntrouvableException("unknown country"));
    }

    /** {@code Number(x)} de JavaScript : la chaîne numérique passe, la chaîne vide vaut 0, le reste est NaN (nul ici). */
    public static Double nombre(Object brut) {
        if (brut instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? d : null;
        }
        if (brut instanceof String s) {
            if (s.isBlank()) {
                return 0d;
            }
            try {
                double d = Double.parseDouble(s.trim());
                return Double.isFinite(d) ? d : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static double positif(Object brut, String message) {
        Double d = nombre(brut);
        if (d == null || d < 0) {
            throw new IllegalArgumentException(message);
        }
        return d;
    }

    /** {@code Number(x) || 12} : nul, absent, zéro ou illisible retombent sur 12. */
    public static int periodes(Object brut) {
        Double p = nombre(brut);
        return p == null || p == 0 ? 12 : (int) (double) p;
    }
}

package ca.vina.nexu.effectifs.domaine;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <strong>Le journal unifié des effectifs</strong> — E-GN-9, E-GN-10 ; le pur de
 * {@code backend/src/effectifs/socle/journal.ts}. Une décision d'un mécanisme automatique est journalisée
 * sous {@link Origine#SYSTEME}, avec le nom du mécanisme, et jamais attribuée à l'utilisateur qui l'a
 * déclenchée sans le savoir.
 */
public final class JournalEffectifs {

    private static final Pattern FORMULE = Pattern.compile("^[=+\\-@].*", Pattern.DOTALL);
    private static final Pattern A_PROTEGER = Pattern.compile("[\",\n;]");
    /** L'en-tête de l'export, dans l'ordre des colonnes du Node. */
    private static final String ENTETE = "horodatage,origine,acteurId,action,objetType,objetId,uniteCode,avant,apres";

    private JournalEffectifs() {
    }

    public enum Origine { UTILISATEUR, SYSTEME }

    /** {@code avant} et {@code apres} sont des JSON déjà sérialisés (ou nuls) : le journal ne réinterprète rien. */
    public record Entree(String id, String horodatage, String acteurId, Origine origine, String action, String objetType,
                         String objetId, String uniteCode, String avantJson, String apresJson) { }

    public record Filtre(String acteurId, Origine origine, String action, String objetType, String objetId, String du, String au) { }

    /** Ce qu'un service consigne. {@code systeme} non nul : un mécanisme, nommé comme acteur. */
    public record Consignation(String action, String objetType, String objetId, String uniteCode, Object avant, Object apres, String systeme) {
        public static Consignation de(String action, String objetType, String objetId, String uniteCode, Object avant, Object apres) {
            return new Consignation(action, objetType, objetId, uniteCode, avant, apres, null);
        }
    }

    public static Origine origineDe(String v) {
        return Arrays.stream(Origine.values()).filter(o -> o.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("origine inconnue : « " + v + " » (attendu : UTILISATEUR, SYSTEME)"));
    }

    /**
     * Une cellule CSV sûre. Guillemets doublés, et une formule neutralisée : une valeur commençant par
     * {@code =}, {@code +}, {@code -} ou {@code @} serait exécutée par un tableur (injection CSV).
     */
    public static String celluleCsv(String v) {
        String s = v == null ? "" : v;
        if (FORMULE.matcher(s).matches()) {
            s = "'" + s;
        }
        return A_PROTEGER.matcher(s).find() ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    public static String enCsv(List<Entree> entrees) {
        StringBuilder b = new StringBuilder(ENTETE).append('\n');
        for (Entree e : entrees) {
            b.append(String.join(",", List.of(celluleCsv(e.horodatage()), celluleCsv(e.origine().name()), celluleCsv(e.acteurId()), celluleCsv(e.action()),
                    celluleCsv(e.objetType()), celluleCsv(e.objetId()), celluleCsv(e.uniteCode()), celluleCsv(e.avantJson()), celluleCsv(e.apresJson())))).append('\n');
        }
        return b.toString();
    }
}

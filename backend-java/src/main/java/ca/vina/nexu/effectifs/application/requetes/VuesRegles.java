package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.ContenuRegle;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.FaitRegle;
import ca.vina.nexu.effectifs.domaine.FicheRegle;
import ca.vina.nexu.effectifs.domaine.Regle;
import ca.vina.nexu.effectifs.domaine.VersionRegle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les formes que le Node publie et journalise ({@code backend/src/effectifs/regles/service.ts}) : une version
 * est aplatie avec sa règle, une condition garde sa forme JSON ({@code tous}, {@code op: "<"}), un type de
 * fait s'écrit en minuscules. Les enregistrements du domaine, sérialisés tels quels, en différeraient.
 */
public final class VuesRegles {

    private VuesRegles() {
    }

    /** Le catalogue de faits par contexte, dans l'ordre des contextes. */
    public static Map<String, Object> faits() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (ContexteRegle c : ContexteRegle.values()) {
            m.put(c.code(), c.faits().stream().map(VuesRegles::fait).toList());
        }
        return m;
    }

    private static Map<String, Object> fait(FaitRegle f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", f.code());
        m.put("type", f.type().code());
        m.put("libelle", f.libelle());
        return m;
    }

    /** Une version comme la ligne du Node : {@code id, regleId, version, cle, contexte, porteeType, porteeCode, condition…}. */
    public static Map<String, Object> version(VersionRegle v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.id());
        m.put("regleId", v.regle().id());
        m.put("version", v.version());
        m.put("cle", v.regle().cle());
        m.put("contexte", v.regle().contexte().code());
        m.put("porteeType", v.regle().porteeType().code());
        m.put("porteeCode", v.regle().porteeCode());
        m.putAll(contenu(v.contenu()));
        m.put("effectifDepuis", v.effectifDepuis());
        m.put("effectifJusqua", v.effectifJusqua());
        return m;
    }

    /** Condition, action, message, priorité : ce qu'une version apporte, dans l'ordre du Node. */
    public static Map<String, Object> contenu(ContenuRegle c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(ContenuRegle.CONDITION, c.condition().enDonnees());
        m.put(ContenuRegle.ACTION, c.action().code());
        m.put(ContenuRegle.MESSAGE, c.message());
        m.put(ContenuRegle.PRIORITE, c.priorite());
        return m;
    }

    /** Une règle avec sa version courante à la date (nulle si retirée). */
    public static Map<String, Object> regle(FicheRegle f, String date) {
        Regle r = f.regle();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("contexte", r.contexte().code());
        m.put("cle", r.cle());
        m.put("porteeType", r.porteeType().code());
        m.put("porteeCode", r.porteeCode());
        m.put("versionCourante", f.courante(date).map(VuesRegles::version).orElse(null));
        return m;
    }

    /** {@code GET /:id} : la règle et toutes ses versions. */
    public static Map<String, Object> fiche(FicheRegle f, String date) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("regle", regle(f, date));
        m.put("versions", f.versions().stream().map(VuesRegles::version).toList());
        return m;
    }

    /** Ce que le journal garde d'une création : l'identité, le contenu, la date de prise d'effet. */
    public static Map<String, Object> creation(Regle r, ContenuRegle c, String depuis) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contexte", r.contexte().code());
        m.put("cle", r.cle());
        m.put("porteeType", r.porteeType().code());
        m.put("porteeCode", r.porteeCode());
        m.putAll(contenu(c));
        m.put("depuis", depuis);
        return m;
    }

    /** Ce que le journal garde d'une nouvelle version. */
    public static Map<String, Object> nouvelleVersion(ContenuRegle c, String depuis, int numero) {
        Map<String, Object> m = new LinkedHashMap<>(contenu(c));
        m.put("depuis", depuis);
        m.put("version", numero);
        return m;
    }

    static List<Map<String, Object>> regles(List<FicheRegle> fiches, String date) {
        return fiches.stream().map(f -> regle(f, date)).toList();
    }
}

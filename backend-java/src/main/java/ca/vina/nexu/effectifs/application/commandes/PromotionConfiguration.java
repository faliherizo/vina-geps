package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.PaquetConfiguration;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.effectifs.domaine.ValeurReferentiel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <strong>Promouvoir la configuration d'un environnement à l'autre</strong> (P-TN-1) : exporter les
 * référentiels et les règles en vigueur, puis les rejouer ailleurs — simulés d'abord, appliqués
 * ensuite. L'application passe par les gestionnaires de saisie : mêmes validations, mêmes
 * versions datées, même journal. Le contrôleur est transactionnel : un import qui échoue en cours
 * de route n'en laisse rien.
 */
public class PromotionConfiguration {

    private static final String REFERENTIELS = "referentiels";
    private static final String REGLES = "regles";
    private static final String OBJET_REGLE = "regle";
    private static final String VERSION_COURANTE = "versionCourante";

    private final LectureReferentiels lectureReferentiels;
    private final GestionnaireReferentiels referentiels;
    private final LectureRegles lectureRegles;
    private final GestionnaireRegles regles;

    public PromotionConfiguration(LectureReferentiels lectureReferentiels, GestionnaireReferentiels referentiels, LectureRegles lectureRegles, GestionnaireRegles regles) {
        this.lectureReferentiels = lectureReferentiels;
        this.referentiels = referentiels;
        this.lectureRegles = lectureRegles;
        this.regles = regles;
    }

    private List<ValeurReferentiel> enVigueur(Contexte ctx, TypeReferentiel type) {
        List<ValeurReferentiel> v = lectureReferentiels.lister(ctx, type.code(), null, false);
        return type == TypeReferentiel.UNITES ? PaquetConfiguration.parentsDabord(v) : v;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> reglesEnVigueur(Contexte ctx) {
        return lectureRegles.lister(ctx, null).stream().filter(r -> r.get(VERSION_COURANTE) instanceof Map<?, ?>).map(r -> {
            Map<String, Object> m = new HashMap<>((Map<String, Object>) r.get(VERSION_COURANTE));
            m.put("id", r.get("id"));
            return m;
        }).toList();
    }

    /** Le paquet : l'en-tête, les valeurs en vigueur par type, et chaque règle avec sa version courante. */
    public Map<String, Object> exporter(Contexte ctx) {
        Map<String, Object> refs = new LinkedHashMap<>();
        for (TypeReferentiel t : TypeReferentiel.values()) {
            refs.put(t.code(), enVigueur(ctx, t).stream().map(PaquetConfiguration::valeur).toList());
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("format", PaquetConfiguration.FORMAT);
        p.put("version", PaquetConfiguration.VERSION);
        p.put("exporteLe", ctx.maintenant());
        p.put("organisme", ctx.tenantId());
        p.put(REFERENTIELS, refs);
        p.put(REGLES, reglesEnVigueur(ctx).stream().map(PaquetConfiguration::regle).toList());
        return p;
    }

    /**
     * Compare le paquet à la configuration en vigueur et, si {@code appliquer}, rejoue les écarts.
     *
     * @param depuis date de prise d'effet des remplacements ; vide = le lendemain
     */
    public Map<String, Object> importer(Contexte ctx, Map<String, Object> paquet, boolean appliquer, String depuis) {
        PaquetConfiguration.verifierEntete(paquet);
        String effet = PaquetConfiguration.priseDEffet(depuis, ctx.aujourdhui());
        List<PaquetConfiguration.Ligne> lignes = new ArrayList<>();
        Map<String, Object> recus = section(paquet.get(REFERENTIELS), REFERENTIELS);
        for (TypeReferentiel t : TypeReferentiel.values()) {
            for (Map<String, Object> v : liste(recus.get(t.code()), REFERENTIELS + "." + t.code())) {
                lignes.add(importerValeur(ctx, t, v, appliquer, effet));
            }
        }
        Map<String, Map<String, Object>> existantes = reglesEnVigueur(ctx).stream()
                .collect(Collectors.toMap(PaquetConfiguration::cleRegle, Function.identity(), (a, b) -> a));
        for (Map<String, Object> r : liste(paquet.get(REGLES), REGLES)) {
            lignes.add(importerRegle(ctx, existantes.get(PaquetConfiguration.cleRegle(r)), r, appliquer, effet));
        }
        return bilan(lignes, appliquer, effet);
    }

    private PaquetConfiguration.Ligne importerValeur(Contexte ctx, TypeReferentiel t, Map<String, Object> recue, boolean appliquer, String effet) {
        String code = String.valueOf(recue.get("code"));
        Map<String, Object> existante = enVigueur(ctx, t).stream().filter(v -> v.code().equals(code)).findFirst().map(PaquetConfiguration::valeur).orElse(null);
        PaquetConfiguration.Action action = PaquetConfiguration.comparerValeur(existante, recue);
        if (appliquer && action == PaquetConfiguration.Action.CREER) {
            referentiels.creer(ctx, t.code(), recue);
        } else if (appliquer && action == PaquetConfiguration.Action.REMPLACER) {
            Map<String, Object> e = new HashMap<>(recue);
            e.put("depuis", effet);
            referentiels.remplacer(ctx, t.code(), code, e);
        }
        return new PaquetConfiguration.Ligne(REFERENTIELS + "." + t.code(), code, action);
    }

    private PaquetConfiguration.Ligne importerRegle(Contexte ctx, Map<String, Object> existante, Map<String, Object> recue, boolean appliquer, String effet) {
        PaquetConfiguration.Action action = PaquetConfiguration.comparerRegle(existante, recue);
        if (appliquer && action == PaquetConfiguration.Action.CREER) {
            regles.creer(ctx, recue);
        } else if (appliquer && action == PaquetConfiguration.Action.REMPLACER) {
            Map<String, Object> e = PaquetConfiguration.contenuRegle(recue);
            e.put("depuis", effet);
            regles.publierVersion(ctx, String.valueOf(existante.get("id")), e);
        }
        return new PaquetConfiguration.Ligne(OBJET_REGLE, PaquetConfiguration.cleRegle(recue), action);
    }

    private static Map<String, Object> bilan(List<PaquetConfiguration.Ligne> lignes, boolean appliquer, String effet) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("applique", appliquer);
        b.put("priseDEffet", effet);
        for (PaquetConfiguration.Action a : PaquetConfiguration.Action.values()) {
            b.put(a.code(), lignes.stream().filter(l -> l.action() == a).count());
        }
        b.put("lignes", lignes.stream().map(PaquetConfiguration.Ligne::enCarte).toList());
        return b;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Object brut, String nom) {
        if (brut == null) {
            return Map.of();
        }
        if (brut instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new EffectifsInvalideException("« " + nom + " » doit être un objet");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> liste(Object brut, String nom) {
        if (brut == null) {
            return List.of();
        }
        if (brut instanceof List<?> l && l.stream().allMatch(Map.class::isInstance)) {
            return (List<Map<String, Object>>) l;
        }
        throw new EffectifsInvalideException("« " + nom + " » doit être une liste d'objets");
    }
}

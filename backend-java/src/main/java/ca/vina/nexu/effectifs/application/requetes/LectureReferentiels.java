package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotReferentielsPort;
import ca.vina.nexu.effectifs.domaine.DescripteurAttribut;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.effectifs.domaine.ValeurReferentiel;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les lectures des référentiels — jumeau de {@code serviceReferentiels.ts}. {@link #exigerValeur} est la
 * porte que les autres lots empruntent : « ce code existe-t-il, et est-il en vigueur à la date de ce fait ? ».
 */
public class LectureReferentiels {

    private final DepotReferentielsPort depot;

    public LectureReferentiels(DepotReferentielsPort depot) {
        this.depot = depot;
    }

    /** Le registre, dans la forme du contrat HTTP du Node (codes en minuscules, clés absentes plutôt que nulles). */
    public List<Map<String, Object>> descripteurs() {
        return Arrays.stream(TypeReferentiel.values()).map(LectureReferentiels::vue).toList();
    }

    private static Map<String, Object> vue(TypeReferentiel t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", t.code());
        m.put("libelle", t.libelle());
        if (t.aDesComportements()) {
            m.put("comportements", t.comportements());
        }
        m.put("attributs", t.attributs().stream().map(LectureReferentiels::vue).toList());
        return m;
    }

    private static Map<String, Object> vue(DescripteurAttribut a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nom", a.nom());
        m.put("libelle", a.libelle());
        m.put("type", a.type().code());
        if (a.obligatoire()) {
            m.put("obligatoire", true);
        }
        if (a.reference() != null) {
            m.put("reference", a.reference().code());
        }
        if (!a.valeurs().isEmpty()) {
            m.put("valeurs", a.valeurs());
        }
        if (a.min() != null) {
            m.put("min", a.min().longValue());
            m.put("max", a.max().longValue());
        }
        return m;
    }

    /** Les valeurs en vigueur à une date (défaut : aujourd'hui), ou tout l'historique du type. */
    public List<ValeurReferentiel> lister(Contexte ctx, String type, String date, boolean historique) {
        TypeReferentiel t = TypeReferentiel.exiger(type);
        if (historique) {
            return depot.historique(ctx.tenantId(), t);
        }
        return depot.enVigueur(ctx.tenantId(), t, date == null || date.isEmpty() ? ctx.aujourdhui() : Referentiels.dateValide(date, "date"));
    }

    public List<ValeurReferentiel> historique(Contexte ctx, String type, String code) {
        TypeReferentiel t = TypeReferentiel.exiger(type);
        List<ValeurReferentiel> v = depot.versions(ctx.tenantId(), t, code);
        if (v.isEmpty()) {
            throw new IntrouvableException(type + " « " + code + " » introuvable");
        }
        return v;
    }

    /** La valeur en vigueur à une date, ou une erreur qui la nomme. */
    public ValeurReferentiel exigerValeur(String tenantId, TypeReferentiel type, String code, String date) {
        return Referentiels.enVigueur(depot.versions(tenantId, type, code), date)
                .orElseThrow(() -> new EffectifsInvalideException(type.libelle() + " : « " + code + " » n'est pas en vigueur le " + date));
    }

    /** Les unités en vigueur (code, parent), pour les périmètres et la garde de cycle. */
    public List<Perimetres.Unite> unitesEnVigueur(String tenantId, String date) {
        return depot.enVigueur(tenantId, TypeReferentiel.UNITES, date).stream()
                .map(v -> new Perimetres.Unite(v.code(), v.attributs().get("parent") instanceof String p ? p : null)).toList();
    }
}

package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.ObjetFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.RegleFlux;

import java.util.LinkedHashMap;
import java.util.Map;

/** Les formes rendues des flux d'approbation, dans l'ordre des clés du Node. */
public final class VuesFlux {

    private VuesFlux() {
    }

    /** Une règle telle que la liste la montre : l'identifiant d'abord. */
    public static Map<String, Object> regle(RegleFlux r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.putAll(saisie(r));
        return m;
    }

    /** Une règle telle que sa création la rend : ce qui a été saisi, puis l'identifiant attribué. */
    public static Map<String, Object> creee(RegleFlux r) {
        Map<String, Object> m = saisie(r);
        m.put("id", r.id());
        return m;
    }

    private static Map<String, Object> saisie(RegleFlux r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objet", r.objet());
        m.put("palier", r.palier());
        m.put("champ", r.champ());
        m.put("operateur", r.operateur().code());
        m.put("valeur", r.valeur());
        m.put("approbateur", r.approbateur());
        return m;
    }

    /** Les champs de routage de chaque objet. */
    public static Map<String, Object> champs() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (ObjetFlux o : ObjetFlux.values()) {
            m.put(o.name(), o.champs().stream().map(VuesFlux::champ).toList());
        }
        return m;
    }

    private static Map<String, Object> champ(FluxApprobation.ChampFlux c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", c.code());
        m.put("libelle", c.libelle());
        m.put("type", c.type().code());
        return m;
    }
}

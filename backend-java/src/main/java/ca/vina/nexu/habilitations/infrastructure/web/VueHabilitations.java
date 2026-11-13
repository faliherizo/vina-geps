package ca.vina.nexu.habilitations.infrastructure.web;

import ca.vina.nexu.habilitations.domaine.Habilitations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La mise en forme JSON du contexte {@code habilitations}.
 *
 * <p>Permission, type d'objet, sujet et origine sont des <strong>enums</strong> dans le domaine, et
 * le Node écrit leurs codes — {@code "lecture_ecriture"}, pas {@code "LECTURE_ECRITURE"}. La
 * conversion se fait <strong>une fois, à la frontière</strong>, comme la conversion inverse à
 * l'entrée : deux API qui ne se ressemblent plus coûtent un écran.
 */
final class VueHabilitations {

    private VueHabilitations() {
    }

    static Map<String, Object> objet(Habilitations.Objet o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", o.type().code());
        m.put("code", o.code());
        m.put("libelle", o.libelle());
        m.put("parentCode", o.parentCode());
        return m;
    }

    static List<Map<String, Object>> objets(List<Habilitations.Objet> objets) {
        return objets.stream().map(VueHabilitations::objet).toList();
    }

    static Map<String, Object> octroi(Habilitations.Octroi o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sujetType", o.sujetType().code());
        m.put("sujetId", o.sujetId());
        m.put("objetType", o.objetType().code());
        m.put("objetCode", o.objetCode());
        m.put("permission", o.permission().code());
        m.put("effectifDu", o.effectifDu());
        m.put("effectifAu", o.effectifAu());
        m.put("motif", o.motif());
        return m;
    }

    static List<Map<String, Object>> octrois(List<Habilitations.Octroi> octrois) {
        return octrois.stream().map(VueHabilitations::octroi).toList();
    }

    static Map<String, Object> resolution(Habilitations.Resolution r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("utilisateurId", r.utilisateurId());
        m.put("objetType", r.objetType().code());
        m.put("objetCode", r.objetCode());
        m.put("permission", r.permission().code());
        m.put("origine", r.origine().code());
        m.put("origineId", r.origineId());
        m.put("motifs", r.motifs());
        return m;
    }

    static Map<String, Object> droits(Habilitations.DroitParObjet d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objet", objet(d.objet()));
        m.put("sujets", d.sujets().stream().map(s -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("sujetType", s.sujetType().code());
            x.put("sujetId", s.sujetId());
            x.put("permission", s.permission().code());
            x.put("effectifAu", s.effectifAu());
            return x;
        }).toList());
        return m;
    }

    static Map<String, Object> fiche(Habilitations.FicheUtilisateur f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("utilisateurId", f.utilisateurId());
        m.put("le", f.le());
        m.put("profils", f.profils());
        m.put("droits", f.droits().stream().map(d -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("objet", objet(d.objet()));
            x.put("permission", d.permission().code());
            x.put("origine", d.origine().code());
            x.put("origineId", d.origineId());
            return x;
        }).toList());
        m.put("echeances", f.echeances().stream().map(e -> Map.<String, Object>of(
                "objetCode", e.objetCode(), "effectifAu", e.effectifAu(),
                "joursRestants", e.joursRestants())).toList());
        m.put("motifs", f.motifs());
        return m;
    }
}

package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.requetes.LectureCascades;
import ca.vina.nexu.alertes.domaine.Cascades;
import ca.vina.nexu.alertes.domaine.Cascades.Cascade;
import ca.vina.nexu.alertes.domaine.Cascades.Politique;

import java.util.LinkedHashMap;
import java.util.Map;

/** Les formes JSON des politiques et des cascades : les codes des enums, et les étapes comptées à partir de 1. */
final class VuesCascades {

    private VuesCascades() {
    }

    static Map<String, Object> politique(Politique p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.id());
        v.put("nom", p.nom());
        v.put("arret", p.arret().code());
        v.put("version", p.version());
        v.put("actif", p.actif());
        v.put("etapes", p.etapes().stream().map(VuesCascades::etape).toList());
        return v;
    }

    static Map<String, Object> etape(Cascades.Etape e) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("cible", e.cible().code());
        v.put("cibleId", e.cibleId());
        v.put("canaux", e.codesCanaux());
        v.put("delaiMinutes", e.delaiMinutes());
        return v;
    }

    static Map<String, Object> suivi(LectureCascades.Suivi s) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("cascade", s.cascade().map(VuesCascades::cascade).orElse(null));
        v.put("traces", s.traces().stream().map(t -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("evenement", t.evenement().code());
            l.put("etape", t.etape() + 1);
            l.put("personnes", t.personnes());
            l.put("instant", t.instant());
            l.put("par", t.par());
            return l;
        }).toList());
        return v;
    }

    private static Map<String, Object> cascade(Cascade c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("politique", politique(c.politique()));
        v.put("etape", c.etape() + 1);
        v.put("etapes", c.politique().etapes().size());
        v.put("echeanceLe", c.echeanceLe());
        v.put("etat", c.etat().code());
        return v;
    }
}

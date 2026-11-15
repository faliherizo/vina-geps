package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.application.commandes.GestionnaireCyclesPaie;
import ca.vina.nexu.paie.application.requetes.LecturePaie;
import ca.vina.nexu.paie.domaine.Bulletin;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.DossierRemuneration;
import ca.vina.nexu.paie.domaine.ReglagesPaie;
import ca.vina.nexu.paie.domaine.Retenues;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Les formes JSON de la paie : les codes des enums, et les totaux qu'un écran ne doit pas recalculer. */
final class VuesPaie {

    private VuesPaie() {
    }

    static Map<String, Object> cycle(CyclePaie c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", c.id());
        v.put("debut", c.debut());
        v.put("fin", c.fin());
        v.put("datePaie", c.datePaie());
        v.put("etat", c.etat().code());
        v.put("prepareLe", c.prepareLe());
        v.put("preparePar", c.preparePar());
        v.put("traiteLe", c.traiteLe());
        v.put("traitePar", c.traitePar());
        return v;
    }

    static Map<String, Object> preparation(GestionnaireCyclesPaie.Preparation p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("cycle", cycle(p.cycle()));
        v.put("bulletins", p.bulletins().stream().map(VuesPaie::bulletin).toList());
        v.put("sansDossier", p.sansDossier());
        return v;
    }

    static Map<String, Object> detail(CyclePaie c, List<Bulletin> bulletins) {
        return Map.of("cycle", cycle(c), "bulletins", bulletins.stream().map(VuesPaie::bulletin).toList());
    }

    static Map<String, Object> bulletin(Bulletin b) {
        Retenues r = b.retenues();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", b.id());
        v.put("cycleId", b.cycleId());
        v.put("ressourceId", b.ressourceId());
        v.put("matricule", b.matricule());
        v.put("nom", b.nom());
        v.put("datePaie", b.datePaie());
        v.put("heuresRegulieres", b.heuresRegulieres());
        v.put("heuresSupplementaires", b.heuresSupplementaires());
        v.put("retenues", r);
        v.put("totalRetenues", r.totalRetenues());
        v.put("net", r.net());
        v.put("cotisationsEmployeur", r.employeur().total());
        v.put("coutEmployeur", r.coutEmployeur());
        v.put("feuilles", b.feuilles());
        return v;
    }

    static Map<String, Object> dossier(LecturePaie.PersonneEtDossier pd) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ressourceId", pd.personne().ressourceId());
        v.put("matricule", pd.personne().matricule());
        v.put("nom", pd.personne().nom());
        v.put("dossier", pd.dossier() == null ? null : dossier(pd.dossier()));
        return v;
    }

    static Map<String, Object> dossier(DossierRemuneration d) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ressourceId", d.ressourceId());
        v.put("mode", d.mode().code());
        v.put("frequence", d.frequence().code());
        v.put("tauxHoraire", d.tauxHoraire());
        v.put("salaireAnnuel", d.salaireAnnuel());
        v.put("montantPersonnelFederal", d.montants().federal());
        v.put("montantPersonnelQuebec", d.montants().quebec());
        return v;
    }

    static Map<String, Object> reglages(ReglagesPaie r, int annee) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("annee", annee);
        v.put("tauxFss", r.tauxFss());
        v.put("tauxCnesst", r.tauxCnesst());
        v.put("majorationSupplementaire", r.majorationSupplementaire());
        return v;
    }
}

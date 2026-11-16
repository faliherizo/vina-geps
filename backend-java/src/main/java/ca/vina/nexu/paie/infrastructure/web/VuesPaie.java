package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.application.commandes.GestionnaireCyclesPaie;
import ca.vina.nexu.paie.application.requetes.LecturePaie;
import ca.vina.nexu.paie.domaine.AnomalieTemps;
import ca.vina.nexu.paie.domaine.Bulletin;
import ca.vina.nexu.paie.domaine.ConditionsRegle;
import ca.vina.nexu.paie.domaine.ControleTemps;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.DossierRemuneration;
import ca.vina.nexu.paie.domaine.Gain;
import ca.vina.nexu.paie.domaine.ReglagesPaie;
import ca.vina.nexu.paie.domaine.ReglePaie;
import ca.vina.nexu.paie.domaine.Retenues;
import ca.vina.nexu.paie.domaine.ValidationTemps;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;

import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        v.put("sansTaux", p.sansTaux());
        v.put("nonValidees", p.nonValidees());
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
        v.put("gains", b.gains().stream().map(VuesPaie::gain).toList());
        return v;
    }

    static Map<String, Object> gain(Gain g) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("code", g.code());
        v.put("libelle", g.libelle());
        v.put("nature", g.nature().code());
        v.put("quantite", g.quantite());
        v.put("taux", g.taux());
        v.put("montant", g.montant());
        return v;
    }

    static Map<String, Object> dossier(LecturePaie.PersonneEtDossier pd) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ressourceId", pd.personne().ressourceId());
        v.put("matricule", pd.personne().matricule());
        v.put("nom", pd.personne().nom());
        v.put("titreEmploi", pd.personne().titreEmploi());
        v.put("convention", pd.personne().convention());
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
        v.put("echelon", d.echelon());
        return v;
    }

    static Map<String, Object> reglages(ReglagesPaie r, int annee) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("annee", annee);
        v.put("tauxFss", r.tauxFss());
        v.put("tauxCnesst", r.tauxCnesst());
        v.put("majorationSupplementaire", r.majorationSupplementaire());
        v.put("validationTempsExigee", r.validationTempsExigee());
        return v;
    }

    // ── Règles de paie variable et validation des temps (ADR 0084) ──────────

    static Map<String, Object> regle(ReglePaie r) {
        ConditionsRegle c = r.conditions();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", r.id());
        v.put("code", r.code());
        v.put("libelle", r.libelle());
        v.put("type", r.type().code());
        v.put("valeur", r.valeur());
        v.put("titreEmploi", c.titreEmploi());
        v.put("convention", c.convention());
        v.put("echelon", c.echelon());
        v.put("heureDebut", c.plage() == null ? null : c.plage().debut().toString());
        v.put("heureFin", c.plage() == null ? null : c.plage().fin().toString());
        v.put("jours", c.jours().stream().map(DayOfWeek::getValue).sorted().toList());
        v.put("feriesSeulement", c.feriesSeulement());
        v.put("codesPaie", c.codesPaie().stream().sorted().toList());
        v.put("valideDu", r.valideDu().toString());
        v.put("valideAu", r.valideAu() == null ? null : r.valideAu().toString());
        v.put("actif", r.actif());
        return v;
    }

    static Map<String, Object> controle(ControleTemps.Etat e) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("feuilles", e.feuilles().stream().map(VuesPaie::ligneControle).toList());
        v.put("sansFeuille", e.sansFeuille().stream().map(VuesPaie::sansFeuille).toList());
        return v;
    }

    private static Map<String, Object> ligneControle(ControleTemps.Ligne l) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("feuilleId", l.feuille().feuilleId());
        v.put("ressourceId", l.feuille().ressourceId());
        v.put("matricule", l.feuille().matricule());
        v.put("nom", l.feuille().nom());
        v.put("debut", l.feuille().debut());
        v.put("etat", l.feuille().etat().name().toLowerCase(Locale.ROOT));
        v.put("heuresRegulieres", l.heuresRegulieres());
        v.put("heuresSupplementaires", l.heuresSupplementaires());
        v.put("anomalies", l.anomalies().stream().map(AnomalieTemps::code).toList());
        v.put("incalculable", l.feuille().incalculable());
        v.put("validee", l.estValidee());
        v.put("validable", l.estValidable());
        v.put("validation", l.validation() == null ? null : validation(l.validation()));
        return v;
    }

    private static Map<String, Object> sansFeuille(PersonnesPourPaiePort.Personne p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ressourceId", p.ressourceId());
        v.put("matricule", p.matricule());
        v.put("nom", p.nom());
        v.put("anomalies", List.of(AnomalieTemps.AUCUNE_FEUILLE.code()));
        return v;
    }

    static Map<String, Object> validation(ValidationTemps t) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", t.id());
        v.put("feuilleId", t.feuilleId());
        v.put("decision", t.decision().code());
        v.put("motif", t.motif());
        v.put("acteur", t.acteur());
        v.put("instant", t.instant());
        return v;
    }
}

package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.ActionRegle;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * L'attribution des places (E-GN-1, E-GN-4, E-GN-11) — jumeau de {@code attribuerPlace} / {@code libererPlace}.
 * Trois filtres, dans cet ordre : <strong>l'impossible</strong> (chevauchement, corps d'emploi) qu'aucun forçage
 * ne lève ; <strong>les règles</strong> de l'organisme (contexte ATTRIBUTION), qu'un forçage justifié au niveau
 * GESTION lève et trace ; les avertissements, rendus sans rien empêcher.
 */
public class GestionnairePlaces {

    /** Sans quart voisin, le repos est tenu pour illimité : une règle de repos minimal ne se déclenche pas. */
    private static final double REPOS_SANS_VOISIN = 999;

    /** Le résultat d'une attribution : l'équipage relu, le verdict des règles, et s'il a fallu forcer. */
    public record Attribution(Map<String, Object> equipage, Regles.Verdict verdict, boolean force) { }

    private final DepotEquipagesPort depot;
    private final LectureEquipages equipages;
    private final LectureRessources ressources;
    private final LecturePerimetres perimetres;
    private final GestionnaireEquipages gestion;
    private final EvaluateurRegles regles;
    private final JournalEffectifsPort journal;

    public GestionnairePlaces(DepotEquipagesPort depot, LectureEquipages equipages, LectureRessources ressources, LecturePerimetres perimetres,
                              GestionnaireEquipages gestion, EvaluateurRegles regles, JournalEffectifsPort journal) {
        this.depot = depot;
        this.equipages = equipages;
        this.ressources = ressources;
        this.perimetres = perimetres;
        this.gestion = gestion;
        this.regles = regles;
        this.journal = journal;
    }

    private Ressource admissible(Contexte ctx, Object ressourceId, Equipage.Place place) {
        Ressource r = ressources.exiger(ctx, ressourceId == null ? "" : String.valueOf(ressourceId));
        if (!r.actif()) {
            throw new EffectifsInvalideException("« " + r.nom() + " » est inactive");
        }
        if (!r.corpsEmploiCode().equals(place.corpsEmploiCode())) {
            throw new ConflitException("« " + r.nom() + " » (" + r.corpsEmploiCode() + ") ne peut pas occuper une place de " + place.corpsEmploiCode());
        }
        return r;
    }

    private Regles.Verdict juger(Contexte ctx, String placeId, Equipage e, Ressource r, List<Planification.Creneau> autres) {
        Map<String, Object> faits = new LinkedHashMap<>();
        faits.put("admissible", true);
        faits.put("reposHeures", Planification.reposHeures(autres, e.creneau()).orElse(REPOS_SANS_VOISIN));
        faits.put("heuresSemaine", Planification.heuresSemaine(autres, e.creneau()));
        faits.put("formationRequiseAcquise", true);
        faits.put("rang", 0);
        faits.put("heures24h", Planification.heuresSur24h(autres, e.creneau()));
        Map<String, String> portees = new HashMap<>();
        portees.put("convention", r.conventionCode());
        portees.put("statutEmploi", r.statutEmploiCode());
        portees.put("corpsEmploi", r.corpsEmploiCode());
        portees.put("titreEmploi", r.titreEmploiCode());
        return regles.evaluerDossier(ctx, new Regles.Dossier(ContexteRegle.ATTRIBUTION, "place", placeId, e.date(), e.uniteCode(), faits, portees));
    }

    /**
     * Attribue une place. {@code systeme} non nul : c'est le moteur (fin de collecte, comblement automatique)
     * qui attribue, et la place le dit — jamais « utilisateur ».
     */
    public Attribution attribuer(Contexte ctx, String placeId, Map<String, Object> entree, String systeme) {
        LectureEquipages.PlaceEtEquipage pe = equipages.lirePlace(ctx, placeId);
        Equipage e = pe.equipage();
        gestion.periodeModifiable(ctx, e.periodeId());
        Ressource r = admissible(ctx, entree.get("ressourceId"), pe.place());
        List<Planification.Creneau> autres = depot.creneauxDe(ctx.tenantId(), r.id(), placeId);
        if (Planification.chevaucheUnQuart(autres, e.creneau())) {
            throw new ConflitException("« " + r.nom() + " » tient déjà un quart qui chevauche celui-ci");
        }
        Regles.Verdict verdict = juger(ctx, placeId, e, r, autres);
        String justification = null;
        if (verdict.action().estBloquant() || verdict.action() == ActionRegle.EXIGER_APPROBATION) {
            if (!Boolean.TRUE.equals(entree.get("forcer"))) {
                throw new RefusParRegleException(verdict.motifs().stream().map(Regles.Motif::message).collect(Collectors.joining(" ; ")), verdict);
            }
            perimetres.exiger(ctx, e.uniteCode(), Perimetres.Niveau.GESTION);
            justification = Planification.justificationDe(entree.get("justification"));
        }
        depot.attribuer(ctx.tenantId(), placeId, r.id(), systeme == null ? Equipage.OrigineAttribution.UTILISATEUR : Equipage.OrigineAttribution.MOTEUR,
                justification, ctx.maintenant());
        consigner(ctx, placeId, e, pe.place(), r, justification, verdict, systeme);
        return new Attribution(LectureEquipages.vue(equipages.lire(ctx, e.id())), verdict, justification != null);
    }

    private void consigner(Contexte ctx, String placeId, Equipage e, Equipage.Place place, Ressource r, String justification, Regles.Verdict verdict,
                           String systeme) {
        Map<String, Object> avant = new HashMap<>();
        avant.put("ressourceId", place.ressourceId());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("ressourceId", r.id());
        apres.put("justification", justification);
        if (justification != null) {
            apres.put("motifsEcartes", verdict.motifs());
        }
        journal.ecrire(ctx, new JournalEffectifs.Consignation(justification != null ? "place.attribution.forcee" : "place.attribuee", "place", placeId,
                e.uniteCode(), avant, apres, systeme));
    }

    public Equipage liberer(Contexte ctx, String placeId, Map<String, Object> entree) {
        LectureEquipages.PlaceEtEquipage pe = equipages.lirePlace(ctx, placeId);
        gestion.periodeModifiable(ctx, pe.equipage().periodeId());
        if (pe.place().ressourceId() == null) {
            throw new EffectifsInvalideException("cette place est déjà vacante");
        }
        String motif = GestionnaireRessources.texte(entree.get("motif"));
        if (motif.isEmpty()) {
            throw new EffectifsInvalideException("« motif » : dites pourquoi la place est libérée");
        }
        depot.liberer(ctx.tenantId(), placeId);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("place.liberee", "place", placeId, pe.equipage().uniteCode(),
                Map.of("ressourceId", pe.place().ressourceId()), Map.of("motif", motif)));
        return equipages.lire(ctx, pe.equipage().id());
    }
}

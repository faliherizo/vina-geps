package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Comportements;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DemandesOperationnelles;
import ca.vina.nexu.effectifs.domaine.DemandesPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.effectifs.domaine.ValeurReferentiel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <strong>Les demandes opérationnelles, lues</strong> (addenda 8, M-08) : les types en vigueur, les siennes, celles du
 * périmètre de l'encadrement, et la liste de temps supplémentaire d'un jour.
 */
public class LectureDemandes {

    private final DemandesPort depot;
    private final LectureReferentiels referentiels;
    private final LectureRessources ressources;
    private final LecturePerimetres perimetres;

    public LectureDemandes(DemandesPort depot, LectureReferentiels referentiels, LectureRessources ressources, LecturePerimetres perimetres) {
        this.depot = depot;
        this.referentiels = referentiels;
        this.ressources = ressources;
        this.perimetres = perimetres;
    }

    public List<ValeurReferentiel> types(Contexte ctx) {
        return referentiels.lister(ctx, TypeReferentiel.TYPES_DEMANDE.code(), null, false);
    }

    public List<Map<String, Object>> miennes(Contexte ctx) {
        Ressource r = ressources.exigerDeLActeur(ctx);
        return depot.deLaRessource(ctx.tenantId(), r.id()).stream().map(d -> vue(d, r)).toList();
    }

    /** Celles du périmètre de l'acteur (niveau Lecture), d'un statut ou toutes. */
    public List<Map<String, Object>> lister(Contexte ctx, String statut) {
        DemandesOperationnelles.Statut s = statut == null || statut.isBlank() ? null : DemandesOperationnelles.Statut.de(statut.strip());
        Map<String, Ressource> gens = gens(ctx);
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        return depot.lister(ctx.tenantId(), s).stream().filter(d -> visibles == null || visibles.contains(d.uniteCode()))
                .map(d -> vue(d, gens.get(d.ressourceId()))).toList();
    }

    /** La liste de temps supplémentaire du jour : les inscriptions acceptées qui le couvrent, par ancienneté. */
    public List<DemandesOperationnelles.Inscrit> listeTempsSupplementaire(Contexte ctx, String date) {
        String jour = Referentiels.dateValide(date, "date");
        List<String> types = referentiels.lister(ctx, TypeReferentiel.TYPES_DEMANDE.code(), jour, false).stream()
                .filter(v -> Comportements.NatureDemande.LISTE_TEMPS_SUPPLEMENTAIRE.name().equals(v.comportement())).map(ValeurReferentiel::code).toList();
        Map<String, Ressource> gens = gens(ctx);
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        return DemandesOperationnelles.listeTempsSupplementaire(depot.accepteesCouvrant(ctx.tenantId(), types, jour).stream()
                .filter(d -> visibles == null || visibles.contains(d.uniteCode())).filter(d -> gens.containsKey(d.ressourceId()))
                .map(d -> {
                    Ressource r = gens.get(d.ressourceId());
                    return new DemandesOperationnelles.Inscrit(r.id(), r.nom(), d.uniteCode(), r.anciennete(), d.du(), d.au());
                }).toList());
    }

    private Map<String, Ressource> gens(Contexte ctx) {
        return ressources.lister(ctx, null, null).stream().collect(Collectors.toMap(Ressource::id, Function.identity()));
    }

    /** La vue d'une demande, nommée par sa personne (son identifiant si l'acteur ne la voit pas). */
    public static Map<String, Object> vue(DemandesOperationnelles.Demande d, Ressource r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id());
        m.put("ressourceId", d.ressourceId());
        m.put("ressourceNom", r == null ? d.ressourceId() : r.nom());
        m.put("uniteCode", d.uniteCode());
        m.put("typeCode", d.typeCode());
        m.put("du", d.du());
        m.put("au", d.au());
        m.put("commentaire", d.commentaire());
        m.put("statut", d.statut().name());
        m.put("creeLe", d.creeLe());
        m.put("decideePar", d.decideePar());
        m.put("decideeLe", d.decideeLe());
        m.put("motifDecision", d.motifDecision());
        return m;
    }
}

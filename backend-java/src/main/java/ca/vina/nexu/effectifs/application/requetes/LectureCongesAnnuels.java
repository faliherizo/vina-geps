package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.CongesAnnuels;
import ca.vina.nexu.effectifs.domaine.CongesAnnuelsPort;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** <strong>Le choix annuel des congés, lu</strong> (addenda 8, M-02) : les campagnes du périmètre, les siennes, les résultats. */
public class LectureCongesAnnuels {

    private final CongesAnnuelsPort depot;
    private final LectureRessources ressources;
    private final LecturePerimetres perimetres;

    public LectureCongesAnnuels(CongesAnnuelsPort depot, LectureRessources ressources, LecturePerimetres perimetres) {
        this.depot = depot;
        this.ressources = ressources;
        this.perimetres = perimetres;
    }

    public CongesAnnuels.Campagne exiger(Contexte ctx, String id) {
        return depot.lire(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("campagne « " + id + " » introuvable"));
    }

    /** Les campagnes des unités que l'acteur voit. */
    public List<CongesAnnuels.Campagne> lister(Contexte ctx) {
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        return depot.lister(ctx.tenantId()).stream().filter(c -> visibles == null || visibles.contains(c.uniteCode())).toList();
    }

    /** Les campagnes qui visent la personne connectée (hors brouillon), avec les semaines offertes et ses choix. */
    public List<Map<String, Object>> miennes(Contexte ctx) {
        Ressource r = ressources.exigerDeLActeur(ctx);
        return depot.lister(ctx.tenantId()).stream().filter(c -> c.etat() != CongesAnnuels.Etat.BROUILLON && c.visePour(r)).map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("campagne", c);
            m.put("semaines", c.semaines());
            m.put("choix", depot.choix(ctx.tenantId(), c.id()).stream().filter(x -> x.ressourceId().equals(r.id())).toList());
            return m;
        }).toList();
    }

    /** Les choix de la campagne, nommés et classés par ancienneté puis par rang. */
    public List<Map<String, Object>> resultats(Contexte ctx, String id) {
        CongesAnnuels.Campagne c = exiger(ctx, id);
        perimetres.exiger(ctx, c.uniteCode(), Perimetres.Niveau.LECTURE);
        Map<String, Ressource> gens = ressources.lister(ctx, c.uniteCode(), null).stream().collect(Collectors.toMap(Ressource::id, Function.identity()));
        return depot.choix(ctx.tenantId(), id).stream().filter(x -> gens.containsKey(x.ressourceId()))
                .sorted(java.util.Comparator.<CongesAnnuels.Choix>comparingDouble(x -> -gens.get(x.ressourceId()).anciennete()).thenComparingInt(CongesAnnuels.Choix::rang))
                .map(x -> {
                    Ressource r = gens.get(x.ressourceId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ressourceId", r.id());
                    m.put("ressourceNom", r.nom());
                    m.put("anciennete", r.anciennete());
                    m.put("rang", x.rang());
                    m.put("semaine", x.semaine());
                    m.put("statut", x.statut().name());
                    m.put("motif", x.motif());
                    return m;
                }).toList();
    }
}

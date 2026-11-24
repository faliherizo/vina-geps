package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotRessourcesPort;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Rattachements;
import ca.vina.nexu.effectifs.domaine.RattachementsPort;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Les lectures des ressources planifiables — jumeau de la partie lecture de {@code serviceRessources.ts}. */
public class LectureRessources {

    private final DepotRessourcesPort depot;
    private final LecturePerimetres perimetres;
    private final RattachementsPort rattachements;

    /** Sans rattachements : chaque personne n'appartient qu'à son unité principale. */
    public LectureRessources(DepotRessourcesPort depot, LecturePerimetres perimetres) {
        this(depot, perimetres, RattachementsPort.AUCUN);
    }

    public LectureRessources(DepotRessourcesPort depot, LecturePerimetres perimetres, RattachementsPort rattachements) {
        this.depot = depot;
        this.perimetres = perimetres;
        this.rattachements = rattachements;
    }

    public Ressource exiger(Contexte ctx, String id) {
        return depot.lire(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("ressource « " + id + " » introuvable"));
    }

    /**
     * Les ressources que l'acteur voit, d'une unité ou de toutes. Addenda 8, M-09 : une personne compte dans chacune de
     * ses unités — la principale, et celles où un rattachement la place aujourd'hui.
     */
    public List<Ressource> lister(Contexte ctx, String uniteCode, String corpsEmploiCode) {
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        java.util.Map<String, Set<String>> autres = Rattachements.unitesLe(rattachements.tous(ctx.tenantId()), ctx.aujourdhui());
        return depot.lister(ctx.tenantId()).stream()
                .filter(r -> visibles == null || unitesDe(r, autres).stream().anyMatch(visibles::contains))
                .filter(r -> vide(uniteCode) || unitesDe(r, autres).contains(uniteCode))
                .filter(r -> vide(corpsEmploiCode) || r.corpsEmploiCode().equals(corpsEmploiCode)).toList();
    }

    private static Set<String> unitesDe(Ressource r, java.util.Map<String, Set<String>> autres) {
        Set<String> u = new java.util.HashSet<>(autres.getOrDefault(r.id(), Set.of()));
        u.add(r.uniteCode());
        return u;
    }

    private static boolean vide(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * La ressource que représente l'utilisateur connecté. Pas de filtre de périmètre : un employé n'en a pas,
     * et c'est de lui-même qu'il s'agit.
     */
    public Optional<Ressource> deLActeur(Contexte ctx) {
        return depot.deLUtilisateur(ctx.tenantId(), ctx.acteurId());
    }

    public Ressource exigerDeLActeur(Contexte ctx) {
        return deLActeur(ctx).orElseThrow(() -> new IntrouvableException(
                "votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur"));
    }

    /** L'acteur agit-il sur lui-même ? Sinon, il agit pour autrui, et il lui faut le périmètre de l'unité. */
    public void exigerDroitSur(Contexte ctx, Ressource r, Perimetres.Niveau niveau) {
        if (r.utilisateurId() != null && r.utilisateurId().equals(ctx.acteurId())) {
            return;
        }
        perimetres.exiger(ctx, r.uniteCode(), niveau);
    }
}

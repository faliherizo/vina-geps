package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.ComptesPort;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.DemandesFlux.Flux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.ObjetFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.RegleFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.TypeApprobateur;
import ca.vina.nexu.effectifs.domaine.FluxApprobationsPort;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Les lectures des flux d'approbation des heures (E-GP-6) — jumeau de {@code lireFlux}, {@code fluxDe}, {@code porte},
 * {@code porteLEtapeCourante} et {@code enAttenteDe} ({@code effectifs/approbations/service.ts}).
 */
public class LectureFluxApprobations {

    private final FluxApprobationsPort moteur;
    private final ComptesPort comptes;
    private final LecturePerimetres perimetres;

    public LectureFluxApprobations(FluxApprobationsPort moteur, ComptesPort comptes, LecturePerimetres perimetres) {
        this.moteur = moteur;
        this.comptes = comptes;
        this.perimetres = perimetres;
    }

    public FluxApprobationsPort moteur() {
        return moteur;
    }

    /** Le nom d'un compte, pour qu'un écran n'affiche pas un identifiant. */
    public String nomDuCompte(String id) {
        return comptes.nomDe(id).orElse(id);
    }

    public List<RegleFlux> regles(String tenantId, ObjetFlux objet) {
        return moteur.regles(tenantId, objet).stream().map(r -> FluxApprobation.depuisRegleApprobation(objet, r)).toList();
    }

    /** Les règles de tous les objets, les champs de routage, et les objets qui suivent encore le flux par défaut. */
    public Map<String, Object> lireFlux(Contexte ctx) {
        List<RegleFlux> regles = Arrays.stream(ObjetFlux.values()).flatMap(o -> regles(ctx.tenantId(), o).stream()).toList();
        Map<String, Object> parDefaut = new LinkedHashMap<>();
        for (ObjetFlux o : ObjetFlux.values()) {
            parDefaut.put(o.name(), regles.stream().noneMatch(r -> r.objet() == o));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("regles", regles.stream().map(VuesFlux::regle).toList());
        m.put("champs", VuesFlux.champs());
        m.put("parDefaut", parDefaut);
        return m;
    }

    public Optional<Flux> fluxDe(String tenantId, ObjetFlux objet, String documentId) {
        return moteur.demandeDuDocument(tenantId, objet, documentId).map(d -> DemandesFlux.vue(d, this::nomDuCompte));
    }

    public boolean porte(Contexte ctx, FluxApprobation.Approbateur a, String uniteCode) {
        return DemandesFlux.porte(a, new DemandesFlux.Acteur(ctx.acteurId(), ctx.role(), ctx.estAdministrateur(),
                n -> perimetres.unitesVisibles(ctx, n).contains(uniteCode)));
    }

    /** L'acteur porte-t-il l'étape courante de ce flux ? (délégation comprise, jugée par le moteur générique) */
    public boolean porteLEtapeCourante(Contexte ctx, Optional<Flux> flux, String uniteCode) {
        return flux.flatMap(Flux::etapeCourante).filter(e -> porte(ctx, e.approbateur(), uniteCode)).isPresent();
    }

    /**
     * Les documents en attente dont l'acteur porte l'étape courante <strong>par son rôle ou nommément</strong> : une personne
     * désignée hors du périmètre de l'unité doit trouver ce qu'elle a à décider. Le niveau de périmètre, lui, se voit déjà par
     * les unités visibles.
     */
    public Set<String> enAttenteDe(Contexte ctx, ObjetFlux objet) {
        return moteur.enCours(ctx.tenantId(), objet).stream()
                .filter(d -> DemandesFlux.vue(d, this::nomDuCompte).etapeCourante()
                        .filter(e -> e.approbateur().type() != TypeApprobateur.PERIMETRE && porte(ctx, e.approbateur(), "")).isPresent())
                .map(DemandesFlux.DemandeApprobation::documentId).collect(Collectors.toSet());
    }
}

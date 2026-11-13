package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.LecturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.PointageEffectifs;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les pointages <strong>à décider</strong> (E-GP-6) — jumeau de {@code exigerPointageADecider}, {@code pointageDetaille} et
 * du filtre {@code aDecider} de {@code listerPointages} : un pointage se voit par le périmètre de son unité, <strong>ou</strong>
 * parce que l'acteur porte l'étape courante de son flux (un rôle ou une personne désignés hors de l'unité).
 */
public class LecturePointagesADecider {

    private final LecturePresenceEffectifs presence;
    private final LectureFluxApprobations flux;

    public LecturePointagesADecider(LecturePresenceEffectifs presence, LectureFluxApprobations flux) {
        this.presence = presence;
        this.flux = flux;
    }

    public LecturePresenceEffectifs presence() {
        return presence;
    }

    public Optional<DemandesFlux.Flux> fluxDe(String tenantId, PointageEffectifs p) {
        return flux.fluxDe(tenantId, FluxApprobation.ObjetFlux.POINTAGE, LecturePresenceEffectifs.documentDe(p));
    }

    /** Le pointage, visible par le périmètre <strong>ou</strong> porté par l'acteur au palier courant de son flux ; sinon 404. */
    public PointageEffectifs exiger(Contexte ctx, String id) {
        PointageEffectifs p = presence.lire(ctx.tenantId(), id);
        boolean porte = p.suivi().statut() == PresenceEffectifs.StatutPointage.A_APPROUVER
                && flux.porteLEtapeCourante(ctx, fluxDe(ctx.tenantId(), p), p.lieu().uniteCode());
        return porte ? p : presence.exiger(ctx, id);
    }

    /** Le pointage et son flux d'approbation : ce que la fiche de l'encadrement affiche. */
    public Map<String, Object> detaille(Contexte ctx, String id) {
        PointageEffectifs p = exiger(ctx, id);
        Map<String, Object> m = new LinkedHashMap<>(presence.vue(ctx.tenantId(), p));
        m.put("flux", fluxDe(ctx.tenantId(), p).orElse(null));
        return m;
    }

    /** Les pointages du périmètre, et ceux qu'une personne désignée par le flux, hors du périmètre, a à décider. */
    public List<Map<String, Object>> lister(Contexte ctx, LecturePointagesEffectifsPort.Filtre filtre) {
        Set<String> portes = ctx.estAdministrateur() ? Set.of() : flux.enAttenteDe(ctx, FluxApprobation.ObjetFlux.POINTAGE);
        return presence.lister(ctx, filtre, portes);
    }
}

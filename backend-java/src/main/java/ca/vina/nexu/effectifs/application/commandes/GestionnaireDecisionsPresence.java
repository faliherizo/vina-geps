package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePointagesADecider;
import ca.vina.nexu.effectifs.application.requetes.LecturePresenceEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.EcriturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.PointageEffectifs;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.StatutPointage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * <strong>La décision de l'encadrement</strong> sur un pointage en attente (E-GP-13, E-GP-6) — jumeau de {@code decider}
 * ({@code presence/service.ts}) : la décision tranche l'étape courante du <strong>flux des pointages</strong>, par qui la porte ;
 * le pointage n'est approuvé qu'au dernier palier, un rejet à n'importe lequel le rejette. Jamais sur ses propres pointages ;
 * une saisie se décide d'un bloc.
 */
public class GestionnaireDecisionsPresence {

    private static final String SUJET = "de ce pointage";

    /** Les écritures d'une décision. */
    public record Ecritures(EcriturePointagesEffectifsPort depot, JournalEffectifsPort journal) { }

    private final LecturePointagesADecider lecture;
    private final LectureRessources ressources;
    private final GestionnaireFluxApprobations flux;
    private final Ecritures ecrire;

    public GestionnaireDecisionsPresence(LecturePointagesADecider lecture, LectureRessources ressources, GestionnaireFluxApprobations flux, Ecritures ecrire) {
        this.lecture = lecture;
        this.ressources = ressources;
        this.flux = flux;
        this.ecrire = ecrire;
    }

    private static String motifDe(Object v) {
        String m = v instanceof String s ? s.strip() : "";
        return m.isEmpty() ? null : m.substring(0, Math.min(m.length(), PresenceEffectifs.TEXTE_MAX));
    }

    private PointageEffectifs decidable(Contexte ctx, String id) {
        PointageEffectifs p = lecture.exiger(ctx, id);
        if (p.suivi().statut() != StatutPointage.A_APPROUVER) {
            throw new ConflitException("ce pointage n'attend pas de décision (" + p.suivi().statut() + ")");
        }
        if (Objects.equals(ressources.exiger(ctx, p.ressourceId()).utilisateurId(), ctx.acteurId())) {
            throw new ConflitException("on ne décide pas de ses propres pointages");
        }
        return p;
    }

    /** Approuve ou rejette un pointage en attente — et, pour une saisie, tout son lot — au palier courant de son flux. */
    public List<Map<String, Object>> decider(Contexte ctx, String id, PresenceEffectifs.DecisionPointage decision, Map<String, Object> entree) {
        PointageEffectifs p = decidable(ctx, id);
        String motif = motifDe(entree.get("motif"));
        if (decision == PresenceEffectifs.DecisionPointage.REJETER && motif == null) {
            throw new EffectifsInvalideException("« motif » : un rejet se motive, la personne le lira");
        }
        LecturePresenceEffectifs presence = lecture.presence();
        List<String> ids = p.saisieId() == null ? List.of(p.id()) : presence.deLaSaisie(ctx.tenantId(), p.saisieId()).stream().map(PointageEffectifs::id).toList();
        fluxDuPointage(ctx, p);
        DemandesFlux.Flux tranche = flux.trancher(ctx, FluxApprobation.ObjetFlux.POINTAGE, LecturePresenceEffectifs.documentDe(p),
                new GestionnaireFluxApprobations.Juge(p.lieu().uniteCode(), decision == PresenceEffectifs.DecisionPointage.APPROUVER
                        ? DemandesFlux.StatutApprobation.APPROUVE : DemandesFlux.StatutApprobation.REJETE, SUJET));
        StatutPointage vers = decision == PresenceEffectifs.DecisionPointage.REJETER ? StatutPointage.REJETE
                : tranche.statut() == DemandesFlux.StatutFlux.APPROUVE ? StatutPointage.APPROUVE : StatutPointage.A_APPROUVER;
        if (vers != StatutPointage.A_APPROUVER) {
            ids.forEach(x -> ecrire.depot().decider(ctx, x, vers, motif));
        }
        journaliser(ctx, p, decision, new Tranche(vers, motif, ids, tranche.palierCourant()));
        return ids.stream().map(x -> presence.vue(ctx.tenantId(), presence.lire(ctx.tenantId(), x))).toList();
    }

    /** Le flux d'un pointage resté en attente d'avant les flux configurables : le flux par défaut, ouvert au premier geste. */
    private DemandesFlux.Flux fluxDuPointage(Contexte ctx, PointageEffectifs p) {
        Optional<DemandesFlux.Flux> existant = lecture.fluxDe(ctx.tenantId(), p);
        if (existant.isPresent()) {
            return existant.get();
        }
        String demandeur = Optional.ofNullable(ressources.exiger(ctx, p.ressourceId()).utilisateurId()).orElse(p.suivi().saisiPar());
        boolean corrobore = lecture.presence().corrobore(ctx.tenantId(), p.ressourceId(), p.court());
        return flux.ouvrir(ctx, FluxApprobation.ObjetFlux.POINTAGE, LecturePresenceEffectifs.documentDe(p), DemandesFlux.contexteDePointage(
                new DemandesFlux.PointageEnAttente(p.lieu().uniteCode(), 0, p.origine() == PresenceEffectifs.OriginePointage.SAISIE, p.constat().ecart() != null,
                        corrobore)), new GestionnaireFluxApprobations.Demande(demandeur, GestionnaireFluxApprobations.UN_PALIER));
    }

    private record Tranche(StatutPointage vers, String motif, List<String> pointages, Integer palier) { }

    private void journaliser(Contexte ctx, PointageEffectifs p, PresenceEffectifs.DecisionPointage decision, Tranche t) {
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("statut", t.vers());
        apres.put("motif", t.motif());
        apres.put("pointages", t.pointages());
        apres.put("palier", t.palier());
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de(decision.actionJournal(), "pointage", p.id(), p.lieu().uniteCode(),
                Map.of("statut", p.suivi().statut()), apres));
    }
}

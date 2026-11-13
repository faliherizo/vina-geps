package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureDisponibilites;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.DepotDisponibilitesPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.DisponibilitesDeposees;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Le dépôt et l'approbation des disponibilités (lot R4) — jumeau de {@code deposer} et {@code approuver}. Un dépôt
 * est résumé en faits, soumis aux règles {@code DISPONIBILITE}, <strong>refusé</strong> si une règle bloque (409,
 * motifs rendus : E-GN-15, E-GN-16), sinon enregistré avec sa conformité.
 */
public class GestionnaireDisponibilites {

    private static final String OBJET = "disponibilites";

    /** Ce que le dépôt consulte, hors de son propre dépôt. */
    public record Lectures(LectureDisponibilites disponibilites, LectureDisponibilites.Lectures planification, LectureReferentiels referentiels) { }

    /** Ce que le dépôt écrit ou déclenche. */
    public record Ecritures(DepotDisponibilitesPort depot, EvaluateurRegles regles, JournalEffectifsPort journal, Sequence sequence) { }

    private final Lectures lire;
    private final Ecritures ecrire;

    public GestionnaireDisponibilites(Lectures lire, Ecritures ecrire) {
        this.lire = lire;
        this.ecrire = ecrire;
    }

    /** L'employé connecté dépose ses propres plages. */
    public DisponibilitesDeposees deposerLesMiennes(Contexte ctx, String periodeId, Map<String, Object> entree) {
        return deposer(ctx, periodeId, lire.planification().ressources().exigerDeLActeur(ctx).id(), entree);
    }

    public DisponibilitesDeposees deposer(Contexte ctx, String periodeId, String ressourceId, Map<String, Object> entree) {
        Ressource r = lire.planification().ressources().exiger(ctx, ressourceId);
        lire.planification().ressources().exigerDroitSur(ctx, r, Perimetres.Niveau.PLANIFICATION);
        Periode p = lire.disponibilites().periodeDe(ctx, periodeId, r);
        Disponibilites.verifierFenetre(p.etat(), p.finCollecte(), ctx.aujourdhui());
        List<Disponibilites.PlageDisponible> plages = Disponibilites.plagesDe(entree.get("plages"), p.debut(), p.fin());
        Regles.Verdict verdict = juger(ctx, r, p, plages);
        if (verdict.action().estBloquant()) {
            throw new RefusParRegleException(verdict.motifs().stream().map(Regles.Motif::message).collect(Collectors.joining(" ; ")), verdict);
        }
        Disponibilites.Conformite conformite = Disponibilites.conformiteDe(verdict.action());
        ecrire.depot().remplacer(ctx.tenantId(), new DepotDisponibilitesPort.NouveauDepot(periodeId, ressourceId, conformite, verdict.motifs(),
                ctx.maintenant(), ctx.acteurId(), plages.stream().map(x -> new DepotDisponibilitesPort.PlageIdentifiee(ecrire.sequence().suivant("edis"), x)).toList()));
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("plages", plages.size());
        apres.put("conformite", conformite);
        apres.put("motifs", verdict.motifs().stream().map(Regles.Motif::cle).toList());
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("disponibilites.deposees", OBJET, periodeId + ":" + ressourceId, r.uniteCode(), null, apres));
        return lire.disponibilites().lire(ctx, periodeId, ressourceId).orElseThrow();
    }

    private Regles.Verdict juger(Contexte ctx, Ressource r, Periode p, List<Disponibilites.PlageDisponible> plages) {
        String statut = r.statutEmploiCode() == null || r.statutEmploiCode().isEmpty() ? ""
                : lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.STATUTS_EMPLOI, r.statutEmploiCode(), p.debut()).comportement();
        Map<String, Object> faits = Disponibilites.faitsDuDepot(plages, p.debut(), p.fin()).enCarte();
        faits.put("statutEmploi", statut == null ? "" : statut);
        faits.put("corpsEmploi", r.corpsEmploiCode());
        return ecrire.regles().evaluerDossier(ctx, new Regles.Dossier(ContexteRegle.DISPONIBILITE, OBJET, p.id() + ":" + r.id(), p.debut(), r.uniteCode(),
                faits, r.porteesDeRegle()));
    }

    /** Un dépôt qui exigeait une approbation est approuvé par un gestionnaire (niveau GESTION). */
    public DisponibilitesDeposees approuver(Contexte ctx, String periodeId, String ressourceId) {
        Ressource r = lire.planification().ressources().exiger(ctx, ressourceId);
        lire.planification().perimetres().exiger(ctx, r.uniteCode(), Perimetres.Niveau.GESTION);
        DisponibilitesDeposees d = lire.disponibilites().lire(ctx, periodeId, ressourceId)
                .orElseThrow(() -> new IntrouvableException("aucun dépôt pour cette personne dans cette période"));
        if (!d.attendApprobation()) {
            throw new EffectifsInvalideException("ce dépôt n'attend aucune approbation");
        }
        ecrire.depot().approuver(ctx.tenantId(), periodeId, ressourceId, new DepotDisponibilitesPort.Approbation(ctx.maintenant(), ctx.acteurId()));
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("disponibilites.approuvees", OBJET, periodeId + ":" + ressourceId, r.uniteCode(), null, null));
        return lire.disponibilites().lire(ctx, periodeId, ressourceId).orElseThrow();
    }
}

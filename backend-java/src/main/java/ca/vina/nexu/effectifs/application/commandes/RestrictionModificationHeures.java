package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.ModificationHeures;
import ca.vina.nexu.effectifs.domaine.ModificationHeures.NiveauActeur;
import ca.vina.nexu.effectifs.domaine.ModificationHeures.OperationModification;
import ca.vina.nexu.effectifs.domaine.PorteesDuDossier;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.SourcesTempsEffectifsPort;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;

import java.util.HashMap;
import java.util.Map;

/**
 * <strong>La restriction de la modification des heures</strong> (E-GP-3) — jumeau de {@code serviceModification.ts}. Chaque
 * ajout, correction ou retrait de segment, chaque saisie de pointage après coup passe par les règles MODIFICATION_HEURES de
 * l'organisme avant d'écrire. La décision est <strong>tracée</strong> même quand elle accepte : un refus se justifie, une
 * acceptation aussi le jour où l'on demande pourquoi une correction tardive est passée.
 */
public class RestrictionModificationHeures {

    /**
     * Une modification demandée.
     *
     * @param uniteCode l'unité du temps touché (celle de la feuille ou du quart)
     * @param objetId   ce que l'évaluation tracée désigne : la feuille, le segment ou la saisie
     */
    public record Demande(OperationModification operation, Ressource ressource, String uniteCode, String date, String finDuTemps, boolean parAutrui,
                          TempsEffectifs.Source sourceSegment, String objetId) { }

    private final EvaluateurRegles regles;
    private final LecturePerimetres perimetres;
    private final SourcesTempsEffectifsPort sources;

    public RestrictionModificationHeures(EvaluateurRegles regles, LecturePerimetres perimetres, SourcesTempsEffectifsPort sources) {
        this.regles = regles;
        this.perimetres = perimetres;
        this.sources = sources;
    }

    /** L'état de la période de l'horaire qui couvre ce jour dans l'unité ; « AUCUNE » hors de toute période. */
    public String etatDeLaPeriode(String tenantId, String uniteCode, String date) {
        return sources.etatPeriode(tenantId, uniteCode, date).map(Enum::name).orElse(ModificationHeures.AUCUNE_PERIODE);
    }

    /** Le plus haut niveau de périmètre de l'acteur sur l'unité. */
    public NiveauActeur niveauDeLActeur(Contexte ctx, String uniteCode) {
        return NiveauActeur.de(ctx.estAdministrateur(), n -> perimetres.unitesVisibles(ctx, n).contains(uniteCode));
    }

    /** Juge la modification ; une règle bloquante la refuse (409, motifs nommés, verdict joint pour l'écran). */
    public void exigerPermise(Contexte ctx, Demande d) {
        Map<String, Object> faits = ModificationHeures.faits(new ModificationHeures.Modification(d.operation(), d.finDuTemps(), ctx.maintenant(),
                etatDeLaPeriode(ctx.tenantId(), d.uniteCode(), d.date()), d.parAutrui(), ctx.role(), niveauDeLActeur(ctx, d.uniteCode()), d.sourceSegment()));
        Regles.Verdict verdict = regles.evaluerDossier(ctx, new Regles.Dossier(ContexteRegle.MODIFICATION_HEURES, ModificationHeures.OBJET, d.objetId(),
                d.date(), d.uniteCode(), faits, portees(d.ressource())));
        if (verdict.action().estBloquant()) {
            throw new RefusParRegleException(ModificationHeures.messageDeRefus(verdict.motifs()), verdict);
        }
    }

    private static Map<String, String> portees(Ressource r) {
        Map<String, String> p = new HashMap<>();
        p.put(PorteesDuDossier.CONVENTION, r.conventionCode());
        p.put(PorteesDuDossier.STATUT_EMPLOI, r.statutEmploiCode());
        p.put(PorteesDuDossier.CORPS_EMPLOI, r.corpsEmploiCode());
        p.put(PorteesDuDossier.TITRE_EMPLOI, r.titreEmploiCode());
        return p;
    }
}

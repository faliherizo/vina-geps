package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureEchangesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.DepotEchangesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.EchangeEffectifs;
import ca.vina.nexu.effectifs.domaine.EchangeEffectifs.Geste;
import ca.vina.nexu.effectifs.domaine.EchangeEffectifs.PlaceDetaillee;
import ca.vina.nexu.effectifs.domaine.EchangeEffectifs.Statut;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les gestes sur les échanges de quarts (lot R6) — jumeau de {@code proposer} et {@code gerer},
 * {@code backend/src/effectifs/echanges/service.ts}.
 * <ul>
 *   <li><strong>proposer</strong> : la demandeuse (ou son planificateur) propose de céder sa place contre celle
 *       d'une collègue ; l'échange est jugé dans les deux sens par les règles ECHANGE. Un refus se dit avant
 *       même d'être proposé (409, motifs nommant la personne concernée).</li>
 *   <li><strong>gérer</strong> : la destinataire accepte ou refuse — l'acceptation rejuge l'échange sur les
 *       horaires du moment ; un échange que les règles veulent approuvé attend la validation de l'encadrement ;
 *       la demandeuse peut annuler tant qu'il n'est pas appliqué.</li>
 * </ul>
 */
public class GestionnaireEchangesEffectifs {

    private static final String PREFIXE = "eech";

    /** Ce que les gestes lisent. */
    public record Lectures(LectureEchangesEffectifs echanges, LectureRessources ressources, LecturePerimetres perimetres, LectureEquipages equipages) { }

    /** Les deux places et les deux personnes, telles qu'elles doivent être pour qu'un échange se tienne. */
    private record Parties(PlaceDetaillee pa, PlaceDetaillee pb, Ressource ra, Ressource rb) { }

    private final Lectures lire;
    private final EvaluateurRegles regles;
    private final DepotEchangesEffectifsPort depot;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public GestionnaireEchangesEffectifs(Lectures lire, EvaluateurRegles regles, DepotEchangesEffectifsPort depot, JournalEffectifsPort journal,
                                         Sequence sequence) {
        this.lire = lire;
        this.regles = regles;
        this.depot = depot;
        this.journal = journal;
        this.sequence = sequence;
    }

    private Parties partiesDe(Contexte ctx, Object placeCedee, Object placeVoulue) {
        PlaceDetaillee pa = lire.echanges().place(ctx, placeCedee);
        PlaceDetaillee pb = lire.echanges().place(ctx, placeVoulue);
        if (!pa.estTenue() || !pb.estTenue()) {
            throw new EffectifsInvalideException("un échange porte sur deux places tenues : une place vacante se comble, elle ne s'échange pas");
        }
        if (pa.ressourceId().equals(pb.ressourceId())) {
            throw new EffectifsInvalideException("on n'échange pas un quart avec soi-même");
        }
        for (PlaceDetaillee p : List.of(pa, pb)) {
            if (p.periodeEtat() != Planification.EtatPeriode.PUBLIEE) {
                throw new EffectifsInvalideException("le quart du " + p.date() + " appartient à une période qui n'est pas publiée : on n'échange que des quarts publiés");
            }
        }
        Ressource ra = lire.ressources().exiger(ctx, pa.ressourceId());
        Ressource rb = lire.ressources().exiger(ctx, pb.ressourceId());
        if (!rb.corpsEmploiCode().equals(pa.corpsEmploiCode()) || !ra.corpsEmploiCode().equals(pb.corpsEmploiCode())) {
            throw new ConflitException(ra.nom() + " et " + rb.nom() + " ne sont pas admissibles aux places l'une de l'autre (corps d'emploi différents)");
        }
        return new Parties(pa, pb, ra, rb);
    }

    /**
     * Juge l'échange dans les deux sens : B reçoit la place de A et cède la sienne, A reçoit celle de B. Chaque
     * sens est une évaluation tracée ; le verdict combiné garde l'action la plus stricte.
     */
    private EchangeEffectifs.VerdictCombine juger(Contexte ctx, String echangeId, Parties p) {
        boolean memePeriode = p.pa().periodeId().equals(p.pb().periodeId());
        EchangeEffectifs.Faits faitsB = EchangeEffectifs.faitsDUnSens(lire.equipages().creneauxDe(ctx, p.rb().id(), p.pb().id()), p.pa().creneau(), memePeriode);
        EchangeEffectifs.Faits faitsA = EchangeEffectifs.faitsDUnSens(lire.equipages().creneauxDe(ctx, p.ra().id(), p.pa().id()), p.pb().creneau(), memePeriode);
        return EchangeEffectifs.combiner(List.of(new EchangeEffectifs.Sens(p.rb().nom(), evaluer(ctx, echangeId, p.rb(), p.pa(), faitsB)),
                new EchangeEffectifs.Sens(p.ra().nom(), evaluer(ctx, echangeId, p.ra(), p.pb(), faitsA))));
    }

    private Regles.Verdict evaluer(Contexte ctx, String echangeId, Ressource r, PlaceDetaillee recue, EchangeEffectifs.Faits faits) {
        Map<String, String> portees = new HashMap<>();
        portees.put("convention", r.conventionCode());
        portees.put("statutEmploi", r.statutEmploiCode());
        portees.put("corpsEmploi", r.corpsEmploiCode());
        portees.put("titreEmploi", r.titreEmploiCode());
        return regles.evaluerDossier(ctx, new Regles.Dossier(ContexteRegle.ECHANGE, EchangeEffectifs.OBJET, echangeId + ":" + r.id(), recue.date(),
                recue.uniteCode(), faits.enCarte(), portees));
    }

    private static void exigerAdmis(EchangeEffectifs.VerdictCombine v) {
        if (v.refuse()) {
            throw new RefusParRegleException(v.message(), v.enVerdict());
        }
    }

    public EchangeEffectifs.Fiche proposer(Contexte ctx, Map<String, Object> entree) {
        Parties p = partiesDe(ctx, entree.get("placeCedeeId"), entree.get("placeVoulueId"));
        lire.ressources().exigerDroitSur(ctx, p.ra(), Perimetres.Niveau.PLANIFICATION);
        Optional<String> ouvert = lire.echanges().ouvertSur(ctx, p.pa().id(), p.pb().id());
        if (ouvert.isPresent()) {
            throw new ConflitException("l'un de ces quarts fait déjà l'objet d'un échange en cours (" + ouvert.get() + ")");
        }
        String id = sequence.suivant(PREFIXE);
        EchangeEffectifs.VerdictCombine verdict = juger(ctx, id, p);
        exigerAdmis(verdict);
        depot.inserer(ctx.tenantId(), new DepotEchangesEffectifsPort.NouvelEchange(id, p.pa(), p.pb(), verdict, ctx.maintenant(), ctx.acteurId()));
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("placeCedeeId", p.pa().id());
        apres.put("placeVoulueId", p.pb().id());
        apres.put("destinataire", p.rb().id());
        apres.put("action", verdict.action().code());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("echange.propose", EchangeEffectifs.OBJET, id, p.pa().uniteCode(), null, apres));
        return lire.echanges().exiger(ctx, id);
    }

    /** Qui peut faire quel geste : la destinataire répond, la demandeuse annule, l'encadrement des deux unités valide. */
    private void exigerDroitAuGeste(Contexte ctx, EchangeEffectifs.Fiche x, Geste geste) {
        switch (geste) {
            case ACCEPTER, REFUSER -> lire.ressources().exigerDroitSur(ctx, lire.ressources().exiger(ctx, x.ressourceBId()), Perimetres.Niveau.PLANIFICATION);
            case ANNULER -> lire.ressources().exigerDroitSur(ctx, lire.ressources().exiger(ctx, x.ressourceAId()), Perimetres.Niveau.PLANIFICATION);
            case VALIDER, REJETER -> {
                lire.perimetres().exiger(ctx, x.uniteA(), Perimetres.Niveau.GESTION);
                lire.perimetres().exiger(ctx, x.uniteB(), Perimetres.Niveau.GESTION);
            }
        }
    }

    /** L'horaire a pu changer depuis la proposition : les places doivent être tenues par les mêmes, et les règles rejugées. */
    private EchangeEffectifs.VerdictCombine rejuger(Contexte ctx, EchangeEffectifs.Fiche x) {
        Parties p = partiesDe(ctx, x.placeCedeeId(), x.placeVoulueId());
        if (!p.pa().ressourceId().equals(x.ressourceAId()) || !p.pb().ressourceId().equals(x.ressourceBId())) {
            throw new ConflitException("l'horaire a changé depuis la proposition : l'une des places n'est plus tenue par la même personne");
        }
        EchangeEffectifs.VerdictCombine v = juger(ctx, x.id(), p);
        exigerAdmis(v);
        return v;
    }

    public EchangeEffectifs.Fiche gerer(Contexte ctx, String id, Geste geste, Map<String, Object> entree) {
        EchangeEffectifs.Fiche x = lire.echanges().exiger(ctx, id);
        exigerDroitAuGeste(ctx, x, geste);
        EchangeEffectifs.VerdictCombine rejuge = geste.rejuge() ? rejuger(ctx, x) : null;
        Statut vers = geste == Geste.ACCEPTER ? EchangeEffectifs.issueDeLAcceptation(x.statut(), rejuge.action()) : EchangeEffectifs.transition(x.statut(), geste);
        String motif = EchangeEffectifs.motifDecision(entree.get("motif"));
        depot.decider(ctx.tenantId(), id, new DepotEchangesEffectifsPort.Decision(vers, rejuge, ctx.maintenant(), ctx.acteurId(), motif));
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("statut", vers);
        apres.put("motif", motif);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("echange." + geste.minuscule(), EchangeEffectifs.OBJET, id, x.uniteA(),
                Map.of("statut", x.statut()), apres));
        if (vers == Statut.APPLIQUE) {
            appliquer(ctx, x);
        }
        return lire.echanges().exiger(ctx, id);
    }

    /** Échange les deux titulaires ; le rejugement vient de vérifier que l'horaire n'a pas bougé. */
    private void appliquer(Contexte ctx, EchangeEffectifs.Fiche x) {
        PlaceDetaillee pa = lire.echanges().place(ctx, x.placeCedeeId());
        PlaceDetaillee pb = lire.echanges().place(ctx, x.placeVoulueId());
        depot.reaffecter(ctx.tenantId(), pa.id(), x.ressourceBId(), ctx.maintenant());
        depot.reaffecter(ctx.tenantId(), pb.id(), x.ressourceAId(), ctx.maintenant());
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put(pa.id(), x.ressourceAId());
        avant.put(pb.id(), x.ressourceBId());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put(pa.id(), x.ressourceBId());
        apres.put(pb.id(), x.ressourceAId());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("echange.applique", EchangeEffectifs.OBJET, x.id(), pa.uniteCode(), avant, apres));
    }
}

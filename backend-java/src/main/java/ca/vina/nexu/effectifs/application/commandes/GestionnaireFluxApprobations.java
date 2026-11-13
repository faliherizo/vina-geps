package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureFluxApprobations;
import ca.vina.nexu.effectifs.application.requetes.VuesFlux;
import ca.vina.nexu.effectifs.domaine.ComptesPort;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.DemandesFlux.EtapeFlux;
import ca.vina.nexu.effectifs.domaine.DemandesFlux.Flux;
import ca.vina.nexu.effectifs.domaine.DemandesFlux.StatutApprobation;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.ObjetFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.RegleApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.RegleFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobationsPort;
import ca.vina.nexu.effectifs.domaine.HorsPerimetreException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les écritures des flux d'approbation des heures (E-GP-6) — jumeau de {@code ajouterRegleFlux}, {@code retirerRegleFlux},
 * {@code ouvrirFlux} et {@code trancherFlux} ({@code effectifs/approbations/service.ts}) : le paramétrage de l'écran, puis
 * l'ouverture d'un flux à la soumission et la décision de son étape courante, <strong>dans l'ordre</strong>.
 */
public class GestionnaireFluxApprobations {

    private static final String OBJET_JOURNAL = "regle-flux";
    /** Le flux par défaut d'un pointage : un palier, au niveau GESTION de l'unité. */
    public static final int UN_PALIER = 1;

    /** Ce qu'une ouverture sait du demandeur et du flux par défaut. */
    public record Demande(String demandeurId, int paliersParDefaut) { }

    /** Ce qu'une décision juge : l'unité du document, la décision, et le sujet nommé dans les refus (« de cette feuille »). */
    public record Juge(String uniteCode, StatutApprobation decision, String sujet) { }

    private final LectureFluxApprobations lecture;
    private final ComptesPort comptes;
    private final JournalEffectifsPort journal;

    public GestionnaireFluxApprobations(LectureFluxApprobations lecture, ComptesPort comptes, JournalEffectifsPort journal) {
        this.lecture = lecture;
        this.comptes = comptes;
        this.journal = journal;
    }

    public LectureFluxApprobations lecture() {
        return lecture;
    }

    private FluxApprobationsPort moteur() {
        return lecture.moteur();
    }

    public Map<String, Object> ajouterRegle(Contexte ctx, Map<String, Object> entree) {
        RegleFlux r = FluxApprobation.regleDeFlux(entree);
        boolean personne = r.approbateur().type() == FluxApprobation.TypeApprobateur.PERSONNE;
        if (personne && !comptes.etablissementDe(r.approbateur().valeur()).filter(ctx.tenantId()::equals).isPresent()) {
            throw new EffectifsInvalideException("« approbateur.valeur » : utilisateur inconnu dans cet établissement");
        }
        RegleFlux regle = r.avecId(moteur().creerRegle(ctx.tenantId(), r.objet(), FluxApprobation.versRegleApprobation(r)));
        Map<String, Object> vue = VuesFlux.creee(regle);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("flux.regle.ajoutee", OBJET_JOURNAL, regle.id(), null, null, vue));
        return vue;
    }

    public Map<String, Object> retirerRegle(Contexte ctx, String id) {
        RegleFlux regle = Arrays.stream(ObjetFlux.values()).flatMap(o -> lecture.regles(ctx.tenantId(), o).stream()).filter(r -> r.id().equals(id))
                .findFirst().orElseThrow(() -> new IntrouvableException("règle de flux « " + id + " » introuvable"));
        moteur().retirerRegle(ctx.tenantId(), id);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("flux.regle.retiree", OBJET_JOURNAL, id, null, VuesFlux.regle(regle), null));
        return Map.of("retiree", id);
    }

    /**
     * Ouvre le flux d'un document. Les règles configurées s'appliquent ; si l'organisme n'en a aucune, <strong>ou si aucune ne
     * s'applique à ce document</strong>, le flux par défaut prend le relais — un document ne reste jamais sans personne pour
     * l'approuver à cause d'un paramétrage incomplet.
     */
    public Flux ouvrir(Contexte ctx, ObjetFlux objet, String documentId, Map<String, Object> contexte, Demande demande) {
        List<RegleApprobation> configurees = moteur().regles(ctx.tenantId(), objet);
        List<RegleApprobation> regles = moteur().sAppliquent(configurees, contexte) ? configurees : FluxApprobation.fluxParDefaut(demande.paliersParDefaut());
        return DemandesFlux.vue(moteur().ouvrir(ctx.tenantId(), new FluxApprobationsPort.NouvelleDemande(objet, documentId, contexte,
                demande.demandeurId(), regles)), lecture::nomDuCompte);
    }

    /**
     * <strong>Décide l'étape courante.</strong> Les paliers se décident dans l'ordre : le moteur générique accepterait n'importe
     * quelle étape en attente, et un palier 2 décidé avant le 1 ferait de l'ordre une suggestion.
     */
    public Flux trancher(Contexte ctx, ObjetFlux objet, String documentId, Juge juge) {
        Optional<Flux> avant = lecture.fluxDe(ctx.tenantId(), objet, documentId);
        EtapeFlux e = avant.flatMap(Flux::etapeCourante).orElseThrow(() -> new ConflitException("aucune approbation n'est attendue " + juge.sujet()));
        if (juge.decision() == StatutApprobation.APPROUVE && avant.get().etapes().stream().anyMatch(x -> ctx.acteurId().equals(x.decidePar()))) {
            throw new ConflitException("vous avez déjà approuvé un palier " + juge.sujet() + " : le palier suivant revient à une autre personne");
        }
        try {
            return DemandesFlux.vue(moteur().decider(ctx.tenantId(), e.id(), new FluxApprobationsPort.DecisionEtape(juge.decision(), ctx.acteurId(), ctx.role(),
                    a -> lecture.porte(ctx, a, juge.uniteCode()))), lecture::nomDuCompte);
        } catch (DemandesFlux.EtapeRefuseeException x) {
            throw switch (x.refus()) {
                case NON_PORTEE -> new HorsPerimetreException("le palier " + e.palier() + " est porté par " + e.libelle() + " : vous ne le portez pas");
                // La séparation des tâches du moteur : qui a soumis ne décide pas (un planificateur qui a soumis la feuille d'une
                // personne sans compte, par exemple).
                case DEMANDEUR -> new ConflitException("qui a soumis ne décide pas de cette approbation : un autre approbateur doit trancher");
                case AUTRE -> new ConflitException(x.getMessage());
            };
        }
    }
}

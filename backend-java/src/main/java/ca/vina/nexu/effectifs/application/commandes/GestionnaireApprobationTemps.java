package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.FeuilleDetailleeTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LectureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * La <strong>soumission et l'approbation à paliers</strong> (E-GP-6, E-RF-4) — jumeau de {@code soumettre} et {@code decider} :
 * une feuille sans anomalie bloquante se soumet, fige ses anomalies et <strong>ouvre son flux d'approbation</strong>, fixé sur
 * ce qu'elle montre à ce moment ; chaque palier se décide dans l'ordre, par qui le porte (niveau de l'unité, rôle ou personne
 * désignée), une personne différente à chaque palier, jamais sa propre feuille.
 */
public class GestionnaireApprobationTemps {

    private static final String SUJET = "de cette feuille";

    /** Les écritures de la soumission et des décisions. */
    public record Ecritures(EcritureFeuillesTempsPort depot, JournalEffectifsPort journal, Sequence sequence) { }

    private final LectureFeuillesTemps lecture;
    private final CalculFeuillesTemps calcul;
    private final GestionnaireFluxApprobations flux;
    private final Ecritures ecrire;

    public GestionnaireApprobationTemps(LectureFeuillesTemps lecture, CalculFeuillesTemps calcul, GestionnaireFluxApprobations flux, Ecritures ecrire) {
        this.lecture = lecture;
        this.calcul = calcul;
        this.flux = flux;
        this.ecrire = ecrire;
    }

    public FeuilleDetailleeTemps soumettre(Contexte ctx, String id) {
        LectureFeuillesTempsPort.FeuilleStockee l = lecture.exigerLigne(ctx, id);
        FeuilleTemps f = l.feuille();
        lecture.exigerEcriture(ctx, f);
        TempsEffectifs.verifierSoumission(f.statut());
        FeuilleDetailleeTemps d = calcul.calculer(ctx, l, true);
        List<TempsEffectifs.Anomalie> bloque = TempsEffectifs.bloquantes(d.anomalies());
        if (!bloque.isEmpty()) {
            throw new ConflitException("à corriger avant de soumettre : " + bloque.stream().map(TempsEffectifs.Anomalie::message).collect(Collectors.joining(" ; ")));
        }
        // E-GP-6 : le flux se fixe à la soumission, sur ce que la feuille montre à ce moment.
        String demandeur = Optional.ofNullable(lecture.ressources().exiger(ctx, f.ressourceId()).utilisateurId()).orElse(ctx.acteurId());
        DemandesFlux.Flux ouvert = flux.ouvrir(ctx, FluxApprobation.ObjetFlux.FEUILLE, id, DemandesFlux.contexteDeFeuille(soumise(d)),
                new GestionnaireFluxApprobations.Demande(demandeur, lecture.parametres(ctx.tenantId()).paliersApprobation()));
        ecrire.depot().soumettre(ctx, id, ouvert.etapes().size(), d.anomalies());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("statut", TempsEffectifs.StatutFeuille.SOUMISE);
        apres.put("anomalies", d.anomalies().size());
        apres.put("paliers", ouvert.etapes().stream().map(DemandesFlux.EtapeFlux::libelle).toList());
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("temps.soumise", "feuille", id, f.uniteCode(), Map.of("statut", f.statut()), apres));
        return calcul.lire(ctx, id);
    }

    /**
     * <strong>Rappeler sa feuille</strong> avant la paie : soumise ou approuvée, elle revient au brouillon pour être corrigée,
     * puis soumise de nouveau. Le circuit en attente est retiré — plus personne n'a à la décider ; un circuit fini reste à
     * l'historique. Qui peut la soumettre peut la rappeler : la personne, ou qui planifie son unité.
     */
    public FeuilleDetailleeTemps rappeler(Contexte ctx, String id, Map<String, Object> entree) {
        FeuilleTemps f = lecture.exigerLigne(ctx, id).feuille();
        lecture.exigerEcriture(ctx, f);
        TempsEffectifs.Issue issue = TempsEffectifs.apresRappel(f.statut());
        if (f.statut() == TempsEffectifs.StatutFeuille.SOUMISE) {
            flux.retirer(ctx, FluxApprobation.ObjetFlux.FEUILLE, id);
        }
        return consigner(ctx, f, TempsEffectifs.Decision.RAPPELER, new Decidee(issue, TempsEffectifs.texteLibre(entree.get("motif"))));
    }

    /**
     * <strong>Rouvrir une feuille approuvée</strong> avant la paie : l'encadrement de l'unité la renvoie à la personne,
     * retournée, avec ce qu'il faut corriger. Une feuille payée ne se rouvre jamais.
     */
    public FeuilleDetailleeTemps rouvrir(Contexte ctx, String id, Map<String, Object> entree) {
        FeuilleTemps f = lecture.exigerLigne(ctx, id).feuille();
        lecture.exigerGestion(ctx, f);
        TempsEffectifs.Issue issue = TempsEffectifs.apresReouverture(f.statut());
        String motif = TempsEffectifs.texteLibre(entree.get("motif"));
        if (motif == null) {
            throw new EffectifsInvalideException("« motif » : dites ce qu'il faut corriger");
        }
        return consigner(ctx, f, TempsEffectifs.Decision.ROUVRIR, new Decidee(issue, motif));
    }

    private FeuilleDetailleeTemps consigner(Contexte ctx, FeuilleTemps f, TempsEffectifs.Decision decision, Decidee d) {
        ecrire.depot().inscrireDecision(ctx, new EcritureFeuillesTempsPort.NouvelleDecision(ecrire.sequence().suivant("efde"), f.id(), f.palier(), decision, d.motif()));
        ecrire.depot().poserIssue(ctx, f.id(), d.issue());
        journaliser(ctx, f, decision, d);
        return calcul.calculer(ctx, lecture.ligne(ctx.tenantId(), f.id()), false);
    }

    /** Ce que la feuille montre à sa soumission : ce sur quoi les paliers se routent. */
    private static DemandesFlux.FeuilleSoumise soumise(FeuilleDetailleeTemps d) {
        long payees = d.segments().stream().filter(s -> TempsEffectifs.estPaye(s.comportement())).mapToLong(s -> TempsEffectifs.minutesDe(s.debut(), s.fin())).sum();
        return new DemandesFlux.FeuilleSoumise(d.uniteCode(), payees, d.heuresSupplementaires().total(),
                d.anomalies().stream().map(TempsEffectifs.Anomalie::code).toList(),
                d.segments().stream().map(s -> new DemandesFlux.SegmentCompte(s.source(), s.version())).toList(),
                d.ecarts().stream().map(TempsEffectifs.EcartPlanifie::ecart).toList());
    }

    public FeuilleDetailleeTemps decider(Contexte ctx, String id, TempsEffectifs.Decision decision, Map<String, Object> entree) {
        FeuilleTemps f = calcul.ligneADecider(ctx, id).feuille();
        if (lecture.estALui(ctx, f.ressourceId())) {
            throw new ConflitException("on n'approuve pas sa propre feuille");
        }
        String motif = motifDe(decision, entree);
        verifierDecidable(f.statut(), decision);
        // Le flux juge qui porte le palier, dans l'ordre, une personne par palier (E-GP-6).
        DemandesFlux.Flux tranche = flux.trancher(ctx, FluxApprobation.ObjetFlux.FEUILLE, id, new GestionnaireFluxApprobations.Juge(f.uniteCode(),
                decision == TempsEffectifs.Decision.APPROUVER ? DemandesFlux.StatutApprobation.APPROUVE : DemandesFlux.StatutApprobation.REJETE, SUJET));
        // Les paliers se décident dans l'ordre : le palier de la feuille suit celui du flux.
        TempsEffectifs.Issue issue = switch (decision) {
            case APPROUVER -> TempsEffectifs.apresApprobation(f.statut(), f.palier(), tranche.etapes().size());
            case RETOURNER -> TempsEffectifs.apresRetour(f.statut());
            // Ce ne sont pas des décisions d'un palier : ils ont leurs propres gestes (rappeler, rouvrir).
            case RAPPELER, ROUVRIR -> throw new EffectifsInvalideException("« " + decision.name() + " » n'est pas une décision d'un palier");
        };
        ecrire.depot().inscrireDecision(ctx, new EcritureFeuillesTempsPort.NouvelleDecision(ecrire.sequence().suivant("efde"), id, f.palier(), decision, motif));
        ecrire.depot().poserIssue(ctx, id, issue);
        journaliser(ctx, f, decision, new Decidee(issue, motif));
        // Relue sans la garde de visibilité : qui vient de décider l'a fait légitimement, même hors périmètre.
        return calcul.calculer(ctx, lecture.ligne(ctx.tenantId(), id), false);
    }

    private static void verifierDecidable(TempsEffectifs.StatutFeuille statut, TempsEffectifs.Decision decision) {
        if (decision == TempsEffectifs.Decision.RETOURNER) {
            TempsEffectifs.apresRetour(statut);
        } else if (statut != TempsEffectifs.StatutFeuille.SOUMISE) {
            throw new ConflitException(statut == TempsEffectifs.StatutFeuille.APPROUVEE ? "feuille déjà approuvée : il n'y a plus rien à décider"
                    : "seule une feuille soumise s'approuve");
        }
    }

    /** Un retour se motive : la personne doit savoir quoi corriger. */
    private static String motifDe(TempsEffectifs.Decision decision, Map<String, Object> entree) {
        String motif = TempsEffectifs.texteLibre(entree.get("motif"));
        if (decision == TempsEffectifs.Decision.RETOURNER && motif == null) {
            throw new EffectifsInvalideException("« motif » : dites ce qu'il faut corriger");
        }
        return motif;
    }

    private record Decidee(TempsEffectifs.Issue issue, String motif) { }

    private void journaliser(Contexte ctx, FeuilleTemps f, TempsEffectifs.Decision decision, Decidee d) {
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("statut", f.statut());
        avant.put("palier", f.palier());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("statut", d.issue().statut());
        apres.put("palier", d.issue().palier());
        apres.put("motif", d.motif());
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de(decision.actionJournal(), "feuille", f.id(), f.uniteCode(), avant, apres));
    }
}

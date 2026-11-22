package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EchangesApi;
import ca.vina.nexu.effectifs.domaine.EchangesApiPort;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LecturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.SoldesBanques;
import ca.vina.nexu.effectifs.domaine.SoldesBanquesPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les écritures de l'API d'échange (26-015-DO, addenda 5 c et d ; AD-06b, AD-06c) : l'organisme transmet ses employés,
 * les soldes de leurs banques, et les absences constatées aux opérations sur un quart réel. Un employé passe par {@link GestionnaireRessources}, comme à l'écran : mêmes référentiels, même
 * journal. Le matricule est la clé partagée avec BD Corpo.
 */
public class GestesEchangesApi {

    /** {@code matricules} : la résolution matricule → ressource, déjà offerte par la présence (horodatages externes). */
    public record Ports(GestionnaireRessources ressources, LectureRessources lecture, LecturePointagesEffectifsPort matricules,
                        SoldesBanquesPort soldes) { }

    /** AD-06c : la déclaration d'absence (même chemin qu'à l'écran) et les quarts exposés, pour situer un quart réel. */
    public record Absences(GestionnaireAbsencesEffectifs gestionnaire, EchangesApiPort quarts) { }

    private final Ports ports;
    private final Absences absences;
    private final JournalEffectifsPort journal;

    public GestesEchangesApi(Ports ports, Absences absences, JournalEffectifsPort journal) {
        this.ports = ports;
        this.absences = absences;
        this.journal = journal;
    }

    /** Crée l'employé s'il est inconnu, sinon le met à jour ; {@code creee} dit lequel (201 ou 200). */
    public record EmployeTransmis(Ressource ressource, boolean creee) { }

    public EmployeTransmis employe(Contexte ctx, String matricule, Map<String, Object> entree) {
        Map<String, Object> champs = new LinkedHashMap<>(entree);
        champs.put("matricule", matricule);
        return ports.matricules().ressourceParMatricule(ctx.tenantId(), matricule)
                .map(id -> new EmployeTransmis(ports.ressources().modifier(ctx, id, champs), false))
                .orElseGet(() -> new EmployeTransmis(ports.ressources().creer(ctx, champs), true));
    }

    /** Pose les soldes reçus pour l'employé ; les banques non nommées gardent le leur. */
    public List<SoldesBanques.Solde> soldes(Contexte ctx, String matricule, Object corps) {
        Ressource r = ressource(ctx, matricule);
        List<SoldesBanques.Solde> soldes = SoldesBanques.lire(r.id(), corps, ctx.maintenant());
        ports.soldes().poser(ctx.tenantId(), soldes);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("ressource.soldes", "ressource", r.id(), r.uniteCode(), null, Map.of("soldes", soldes)));
        return ports.soldes().deLaRessource(ctx.tenantId(), r.id());
    }

    public List<SoldesBanques.Solde> soldesDe(Contexte ctx, String matricule) {
        return ports.soldes().deLaRessource(ctx.tenantId(), ressource(ctx, matricule).id());
    }

    /**
     * L'absence constatée aux opérations : sur un quart réel, elle en prend la date, et le quart doit être tenu par cet
     * employé ; sinon, sur les dates transmises.
     */
    public GestionnaireAbsencesEffectifs.Constat absence(Contexte ctx, String matricule, Map<String, Object> corps) {
        Ressource r = ressource(ctx, matricule);
        EchangesApi.AbsenceOperations a = EchangesApi.AbsenceOperations.de(corps);
        GestionnaireAbsencesEffectifs.Declaration d = a.quartId() == null
                ? new GestionnaireAbsencesEffectifs.Declaration(r.id(), a.codeAbsence(), a.du(), a.au(), a.motif())
                : surLeQuart(ctx, r, matricule, a);
        return absences.gestionnaire().constater(ctx, d);
    }

    private GestionnaireAbsencesEffectifs.Declaration surLeQuart(Contexte ctx, Ressource r, String matricule, EchangesApi.AbsenceOperations a) {
        EchangesApi.Quart q = Optional.ofNullable(absences.quarts().quartsExposesParId(ctx.tenantId(), List.of(a.quartId())).get(a.quartId()))
                .orElseThrow(() -> new IntrouvableException("quart « " + a.quartId() + " » introuvable ou non publié"));
        // Une absence déjà constatée ce jour a vidé la place : le renvoi se juge alors à la déclaration (même absence ou conflit).
        if (q.places().stream().noneMatch(p -> r.id().equals(p.ressourceId())) && !absences.gestionnaire().estAbsentLe(ctx, r.id(), q.date())) {
            throw new ConflitException("l'employé « " + matricule + " » ne tient aucune place du quart « " + q.id() + " » du " + q.date());
        }
        return new GestionnaireAbsencesEffectifs.Declaration(r.id(), a.codeAbsence(), q.date(), q.date(), a.motif());
    }

    private Ressource ressource(Contexte ctx, String matricule) {
        String id = ports.matricules().ressourceParMatricule(ctx.tenantId(), matricule)
                .orElseThrow(() -> new IntrouvableException("employé « " + matricule + " » inconnu : transmettez-le d'abord"));
        return ports.lecture().exiger(ctx, id);
    }
}

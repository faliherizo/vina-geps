package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
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

/**
 * Les écritures de l'API d'échange (26-015-DO, addenda 5 c ; AD-06b) : l'organisme transmet ses employés et les soldes
 * de leurs banques. Un employé passe par {@link GestionnaireRessources}, comme à l'écran : mêmes référentiels, même
 * journal. Le matricule est la clé partagée avec BD Corpo.
 */
public class GestesEchangesApi {

    /** {@code matricules} : la résolution matricule → ressource, déjà offerte par la présence (horodatages externes). */
    public record Ports(GestionnaireRessources ressources, LectureRessources lecture, LecturePointagesEffectifsPort matricules,
                        SoldesBanquesPort soldes) { }

    private final Ports ports;
    private final JournalEffectifsPort journal;

    public GestesEchangesApi(Ports ports, JournalEffectifsPort journal) {
        this.ports = ports;
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

    private Ressource ressource(Contexte ctx, String matricule) {
        String id = ports.matricules().ressourceParMatricule(ctx.tenantId(), matricule)
                .orElseThrow(() -> new IntrouvableException("employé « " + matricule + " » inconnu : transmettez-le d'abord"));
        return ports.lecture().exiger(ctx, id);
    }
}

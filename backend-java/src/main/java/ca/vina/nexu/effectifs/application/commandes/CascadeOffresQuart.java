package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotOffresQuartPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.EcheancesOffresPort;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.OffreQuart;
import ca.vina.nexu.effectifs.domaine.OffresQuart;
import ca.vina.nexu.effectifs.domaine.OffresQuart.EtatOffre;
import ca.vina.nexu.effectifs.domaine.OffresQuart.EtatRang;
import ca.vina.nexu.effectifs.domaine.OffresQuart.Evenement;
import ca.vina.nexu.effectifs.domaine.ParametresOffresPort;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.Role;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * <strong>L'offre d'un quart par ancienneté et à tour de rôle</strong> (AD-07 ; art. 15.10, 16.02, 16.03 ; ADR 0089) :
 * le mécanisme qui ouvre l'offre, la propose rang par rang, fait expirer le silence et passe au suivant.
 *
 * <p>Le temps avance de deux façons, qui aboutissent au même état : la tâche de l'ordonnanceur planifiée à chaque
 * échéance, et le <strong>rattrapage</strong> au début de toute lecture ou de tout geste sur les offres — une boucle
 * arrêtée ne laisse donc jamais une personne répondre à une offre échue. Chaque geste est journalisé sous le nom du
 * mécanisme.
 */
public class CascadeOffresQuart {

    /** Une place offerte, avec son comblement, son quart et ses admissibles. */
    public record Ouverture(String comblementId, String absenceId, String placeId, Equipage equipage, List<Disponibilites.Candidature> admissibles) { }

    /** Ce que l'offre tient : ses rangs, le réglage de l'organisme, le réveil de l'ordonnanceur. */
    public record Ports(DepotOffresQuartPort depot, ParametresOffresPort parametres, EcheancesOffresPort echeances) { }

    /** Ce qui l'entoure : les avis, la lecture des places, le journal, les identifiants. */
    public record Collaborateurs(AvisDeComblement avis, LectureEquipages equipages, JournalEffectifsPort journal, Sequence sequence) { }

    private static final String OBJET = "offre";
    private static final String LIEN = "/admin/monhoraire";

    private final Ports ports;
    private final Collaborateurs avec;

    public CascadeOffresQuart(Ports ports, Collaborateurs avec) {
        this.ports = ports;
        this.avec = avec;
    }

    /** Le contexte du mécanisme : il lit sans périmètre, à l'instant reçu. */
    static Contexte duMecanisme(String tenantId, String maintenant) {
        return new Contexte(tenantId, OffresQuart.MECANISME, Role.ADMIN.code(), maintenant);
    }

    public boolean estEnCascade(String tenantId) {
        return ports.parametres().lire(tenantId).estEnCascade();
    }

    /** Ouvre l'offre d'une place : l'ordre est figé ici, puis le premier rang — ou tous, si le quart est urgent — est avisé. */
    public OffreQuart ouvrir(Contexte ctx, Ouverture o) {
        OffresQuart.Parametres p = ports.parametres().lire(ctx.tenantId());
        Equipage e = o.equipage();
        boolean urgente = OffresQuart.estUrgente(ctx.maintenant(), debutDuQuart(e), p.seuilUrgenceMinutes());
        List<OffresQuart.Candidat> ordre = OffresQuart.ordonner(candidats(ctx, o.admissibles(), p));
        double heures = OffresQuart.heuresDuQuart(e.debut(), e.fin());
        List<OffreQuart.Rang> rangs = IntStream.range(0, ordre.size()).mapToObj(i -> rang(i + 1, ordre.get(i), heures)).toList();
        OffreQuart offre = new OffreQuart(avec.sequence().suivant("eofr"), o.comblementId(), o.absenceId(),
                new OffreQuart.Quart(o.placeId(), e.uniteCode(), e.date(), e.debut(), e.fin()), urgente, EtatOffre.OUVERTE, ctx.maintenant(), null, rangs);
        ports.depot().ouvrir(ctx.tenantId(), offre);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("placeId", o.placeId());
        apres.put("urgente", urgente);
        apres.put("ordre", ordre.stream().map(OffresQuart.Candidat::ressourceId).toList());
        consigner(ctx, offre, Evenement.OUVERTE, apres);
        avancer(ctx, offre.id());
        return relire(ctx, offre.id());
    }

    private List<OffresQuart.Candidat> candidats(Contexte ctx, List<Disponibilites.Candidature> admissibles, OffresQuart.Parametres p) {
        Map<String, Double> comptees = ports.depot().heuresComptees(ctx.tenantId(), admissibles.stream().map(Disponibilites.Candidature::ressourceId).toList(),
                OffresQuart.debutFenetre(ctx.maintenant(), p.fenetreRepartitionJours()), EtatRang.comptes());
        return admissibles.stream().map(c -> new OffresQuart.Candidat(c.ressourceId(), c.nom(), c.anciennete(), comptees.getOrDefault(c.ressourceId(), 0.0)))
                .toList();
    }

    private static OffreQuart.Rang rang(int n, OffresQuart.Candidat c, double heures) {
        return new OffreQuart.Rang(n, c.ressourceId(), c.nom(), c.anciennete(), c.heuresComptees(), heures, EtatRang.EN_ATTENTE, null, null, null, null);
    }

    private static String debutDuQuart(Equipage e) {
        return PresenceEffectifs.instantLocal(e.date(), e.debut());
    }

    /** Ce que l'ordonnanceur appelle à l'échéance d'une proposition. */
    public void executer(String tenantId, String offreId, String maintenant) {
        avancer(duMecanisme(tenantId, maintenant), offreId);
    }

    /** Traite toutes les propositions échues de l'organisme à l'instant du contexte. */
    public void rattraper(Contexte ctx) {
        ports.depot().ouvertesEchues(ctx.tenantId(), ctx.maintenant()).forEach(id -> avancer(duMecanisme(ctx.tenantId(), ctx.maintenant()), id));
    }

    OffreQuart relire(Contexte ctx, String offreId) {
        return ports.depot().lire(ctx.tenantId(), offreId).orElseThrow(() -> new IntrouvableException("offre de quart introuvable : " + offreId));
    }

    /**
     * Fait avancer une offre ouverte. Une place pourvue autrement (par le planificateur) retire l'offre ; sinon le
     * silence échu expire (réputé refus, 16.02) et, s'il ne reste aucune proposition en cours, le rang suivant est
     * avisé — ou l'offre s'épuise et la place va à la gestion régulière.
     */
    public void avancer(Contexte ctx, String offreId) {
        OffreQuart o = relire(ctx, offreId);
        if (!o.estOuverte()) {
            return;
        }
        if (estPourvue(ctx, o)) {
            terminer(ctx, o, EtatOffre.RETIREE, Evenement.RETIREE);
            return;
        }
        o.echus(ctx.maintenant()).forEach(r -> expirer(ctx, o, r));
        OffreQuart apres = relire(ctx, offreId);
        if (apres.aUnRangOffert()) {
            return;
        }
        List<OffreQuart.Rang> aAviser = apres.aAviser();
        if (aAviser.isEmpty()) {
            terminer(ctx, apres, EtatOffre.EPUISEE, Evenement.EPUISEE);
            return;
        }
        aAviser.forEach(r -> proposer(ctx, apres, r));
    }

    private void terminer(Contexte ctx, OffreQuart o, EtatOffre etat, Evenement ev) {
        fermer(ctx, o, etat, null);
        consigner(ctx, o, ev, Map.of("placeId", o.quart().placeId()));
    }

    private boolean estPourvue(Contexte ctx, OffreQuart o) {
        return avec.equipages().lirePlace(ctx, o.quart().placeId()).place().ressourceId() != null;
    }

    /** À une offre urgente, le silence n'est pas un refus : le délai de dix minutes ne s'appliquait pas (16.02). */
    private void expirer(Contexte ctx, OffreQuart o, OffreQuart.Rang r) {
        EtatRang etat = o.urgente() ? EtatRang.CLOS : EtatRang.EXPIRE;
        ports.depot().repondre(ctx.tenantId(), new DepotOffresQuartPort.Reponse(o.id(), r.ressourceId(), etat, r.echeance(), null));
        consigner(ctx, o, Evenement.EXPIREE, Map.of("ressourceId", r.ressourceId(), "etat", etat));
    }

    private void proposer(Contexte ctx, OffreQuart o, OffreQuart.Rang r) {
        OffresQuart.Parametres p = ports.parametres().lire(ctx.tenantId());
        String echeance = OffresQuart.echeance(ctx.maintenant(), debutDuQuart(o), o.urgente(), p.delaiReponseMinutes());
        ports.depot().proposer(ctx.tenantId(), new DepotOffresQuartPort.Proposition(o.id(), r.ressourceId(), ctx.maintenant(), echeance));
        ports.echeances().planifier(ctx.tenantId(), o.id(), echeance);
        OffreQuart.Quart q = o.quart();
        String libelle = "le " + q.date() + " de " + GestionnaireComblementAbsences.hhmm(q.debut()) + " à " + GestionnaireComblementAbsences.hhmm(q.fin())
                + " (" + q.uniteCode() + ")";
        avec.avis().aviser(ctx, o.comblementId(), r.ressourceId(), new AvisDeComblement.Message("quart.offert", "Quart offert le " + q.date(),
                "Le quart " + libelle + " vous est offert, à votre tour selon l'ancienneté et le tour de rôle. Répondez avant le " + OffresQuart.heureLocale(echeance)
                        + " dans « Mon horaire » : sans réponse, le quart est réputé refusé.", LIEN));
        consigner(ctx, o, Evenement.PROPOSEE, Map.of("ressourceId", r.ressourceId(), "rang", r.rang(), "echeance", echeance));
    }

    private static String debutDuQuart(OffreQuart o) {
        return PresenceEffectifs.instantLocal(o.quart().date(), o.quart().debut());
    }

    /** Ferme l'offre : les rangs encore offerts ou en attente sont clos, le réveil est oublié. Le journal revient à l'appelant. */
    void fermer(Contexte ctx, OffreQuart o, EtatOffre etat, String attribueeA) {
        ports.depot().fermer(ctx.tenantId(), new DepotOffresQuartPort.Fermeture(o.id(), etat, attribueeA, ctx.maintenant()));
        ports.echeances().annuler(ctx.tenantId(), o.id());
    }

    /** Un geste du mécanisme, au journal sous son nom. */
    private void consigner(Contexte ctx, OffreQuart o, Evenement ev, Map<String, Object> apres) {
        avec.journal().ecrire(ctx, new JournalEffectifs.Consignation(ev.code(), OBJET, o.id(), o.quart().uniteCode(), null, apres, OffresQuart.MECANISME));
    }
}

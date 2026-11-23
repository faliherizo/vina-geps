package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;
import ca.vina.nexu.alertes.domaine.Alertes.Remise;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;
import ca.vina.nexu.alertes.domaine.AppelsAlertes;
import ca.vina.nexu.alertes.domaine.ArmementCascades;
import ca.vina.nexu.alertes.domaine.Cascades;
import ca.vina.nexu.alertes.domaine.Cascades.Cascade;
import ca.vina.nexu.alertes.domaine.Cascades.Etape;
import ca.vina.nexu.alertes.domaine.Cascades.Evenement;
import ca.vina.nexu.alertes.domaine.Cascades.Politique;
import ca.vina.nexu.alertes.domaine.EcheancierCascades;
import ca.vina.nexu.alertes.domaine.EcritureAlertesPort;
import ca.vina.nexu.alertes.domaine.EcritureCascadesPort;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.LectureCascadesPort;
import ca.vina.nexu.alertes.domaine.ReactionsAlertes;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.alertes.domaine.UniteDeTravailAlertes;
import ca.vina.nexu.partage.disponibilite.DisponibiliteDestinatairesPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <strong>Orchestrer les cascades</strong> (ST-G04 ; critères 15, 16, 17, 19, 25). Armer à l'envoi ; exécuter une
 * étape à son échéance ; arrêter dès que le critère est atteint ; reprendre à une étape choisie.
 *
 * <p>Une étape s'exécute en une unité de travail : la cascade avance d'abord, par un verrou optimiste — si elle a
 * déjà bougé, une exécution concurrente ou reprise ne fait rien —, puis les remises et les échéances s'écrivent.
 * L'envoi part par une tâche durable à part : un redémarrage ne le perd pas.
 */
public final class GestionnaireCascades implements ArmementCascades, ReactionsAlertes {

    /** Le mécanisme qui signe la trace quand personne n'a agi. */
    public static final String MECANISME = "cascade";
    private static final String PREFIXE_RELANCE = "[relance] ";

    /** Ce que le gestionnaire lit et écrit, groupé pour garder le constructeur court. */
    public record Dependances(LectureCascadesPort lecture, EcritureCascadesPort ecriture, LectureAlertesPort alertes,
                              EcritureAlertesPort ecritureAlertes, AnnuaireAlertes annuaire) { }

    /** Les échéances, la disponibilité, la transaction. */
    public record Services(EcheancierCascades echeancier, DisponibiliteDestinatairesPort disponibilite, UniteDeTravailAlertes unite) { }

    private final Dependances d;
    private final Services s;
    private final Clock horloge;

    public GestionnaireCascades(Dependances d, Services s, Clock horloge) {
        this.d = d;
        this.s = s;
        this.horloge = horloge;
    }

    @Override
    public void armer(Alerte a, String politiqueId) {
        if (politiqueId == null || politiqueId.isBlank()) {
            return;
        }
        Politique p = d.lecture().politique(a.tenantId(), politiqueId).orElseThrow(() -> RefusAlerte.invalide("« politiqueId » : politique introuvable"));
        Cascade c = Cascade.armer(a.id(), p, a.envoyeeLe());
        d.ecriture().armer(a.tenantId(), c);
        tracer(a.tenantId(), c, Evenement.ARMEE, List.of(), a.envoyeeLe(), a.expediteurId());
        s.echeancier().planifierEtape(a.tenantId(), a.id(), 0, c.echeanceLe());
    }

    /** L'exécution d'une étape à son échéance, par l'ordonnanceur. Ne fait rien si la cascade n'attend plus cette étape. */
    public void executerEtape(String tenantId, String alerteId, int etape, String maintenant) {
        s.unite().executer(() -> d.lecture().cascade(tenantId, alerteId).filter(c -> c.attend(etape))
                .ifPresent(c -> executer(tenantId, c, maintenant)));
    }

    private void executer(String tenantId, Cascade c, String maintenant) {
        Alerte a = d.alertes().alerte(tenantId, c.alerteId()).orElseThrow(() -> RefusAlerte.introuvable("alerte introuvable"));
        if (c.politique().arret().atteint(a)) {
            arreter(tenantId, c, maintenant, MECANISME);
            return;
        }
        Cascade suite = c.apresEtape(maintenant);
        if (!d.ecriture().avancer(tenantId, c, suite)) {
            return;
        }
        List<Destinataire> vises = vises(tenantId, a, c, maintenant);
        ajouterNouveaux(tenantId, a, vises, maintenant);
        relancer(tenantId, a, c, vises, maintenant);
        tracer(tenantId, c, Evenement.ETAPE, vises.stream().map(Destinataire::personId).toList(), maintenant, MECANISME);
        planifierSuite(tenantId, suite, maintenant);
    }

    private void planifierSuite(String tenantId, Cascade suite, String maintenant) {
        if (suite.etat() == Cascades.Etat.EPUISEE) {
            tracer(tenantId, suite, Evenement.EPUISEE, List.of(), maintenant, MECANISME);
            return;
        }
        s.echeancier().planifierEtape(tenantId, suite.alerteId(), suite.etape(), suite.echeanceLe());
    }

    /** Une lecture ou une réponse : si le critère est atteint, la cascade s'arrête maintenant, et son échéance s'annule. */
    @Override
    public void apresReaction(String tenantId, String alerteId, String instant) {
        d.lecture().cascade(tenantId, alerteId).filter(c -> c.etat() == Cascades.Etat.ARMEE).ifPresent(c ->
                d.alertes().alerte(tenantId, alerteId).filter(a -> c.politique().arret().atteint(a))
                        .ifPresent(a -> s.unite().executer(() -> arreter(tenantId, c, instant, MECANISME))));
    }

    /** La reprise manuelle à l'étape choisie (1 = la première) ; le geste est tracé au nom de l'acteur (critère 17). */
    public Cascade reprendre(Acteur acteur, String alerteId, int etapeHumaine) {
        Cascade c = d.lecture().cascade(acteur.tenantId(), alerteId).orElseThrow(() -> RefusAlerte.introuvable("cette alerte n'a pas de cascade"));
        String maintenant = Instant.now(horloge).toString();
        Cascade reprise = c.reprise(etapeHumaine - 1, maintenant);
        s.unite().executer(() -> {
            if (!d.ecriture().avancer(acteur.tenantId(), c, reprise)) {
                throw RefusAlerte.conflit("la cascade vient de changer : rechargez, puis reprenez");
            }
            tracer(acteur.tenantId(), reprise, Evenement.REPRISE, List.of(), maintenant, acteur.personId());
            s.echeancier().planifierEtape(acteur.tenantId(), alerteId, reprise.etape(), maintenant);
        });
        return reprise;
    }

    private void arreter(String tenantId, Cascade c, String instant, String par) {
        if (d.ecriture().avancer(tenantId, c, c.arretee())) {
            s.echeancier().annulerEtape(tenantId, c.alerteId());
            tracer(tenantId, c, Evenement.ARRETEE, List.of(), instant, par);
        }
    }

    // ── Qui viser ────────────────────────────────────────────────────────────

    List<Destinataire> vises(String tenantId, Alerte a, Cascade c, String maintenant) {
        Etape e = c.etapeCourante();
        List<Destinataire> l = switch (e.cible()) {
            case INITIAUX -> sansReaction(a, c.politique().arret());
            case GROUPE -> d.annuaire().membres(tenantId, e.cibleId());
            case PERSONNE -> d.annuaire().personne(tenantId, e.cibleId()).map(List::of).orElse(List.of());
            case PROCHAIN_DISPONIBLE -> prochainDisponible(tenantId, a, e.cibleId(), maintenant);
        };
        return l.stream().filter(x -> !x.personId().equals(a.expediteurId())).distinct().toList();
    }

    /** Ceux qui n'ont pas réagi au sens du critère : pas répondu (prise en charge), ou pas lu (toutes lues). */
    private static List<Destinataire> sansReaction(Alerte a, Cascades.CritereArret arret) {
        return a.remises().stream().filter(r -> switch (arret) {
            case PRISE_EN_CHARGE -> !r.repondue();
            case TOUTES_LUES -> !r.lue();
        }).map(Remise::destinataire).toList();
    }

    /** Le premier membre du groupe, pas encore visé par l'alerte, disponible maintenant ; personne s'il n'y en a aucun. */
    private List<Destinataire> prochainDisponible(String tenantId, Alerte a, String groupeId, String maintenant) {
        Set<String> deja = a.remises().stream().map(r -> r.destinataire().personId()).collect(Collectors.toSet());
        return d.annuaire().membres(tenantId, groupeId).stream()
                .filter(m -> !deja.contains(m.personId()) && !m.personId().equals(a.expediteurId()))
                .filter(m -> s.disponibilite().disponible(tenantId, m.personId(), maintenant)).limit(1).toList();
    }

    private void ajouterNouveaux(String tenantId, Alerte a, List<Destinataire> vises, String maintenant) {
        Set<String> deja = a.remises().stream().map(r -> r.destinataire().personId()).collect(Collectors.toSet());
        List<Remise> nouvelles = new ArrayList<>();
        vises.stream().filter(v -> !deja.contains(v.personId())).forEach(v -> nouvelles.add(new Remise(v, maintenant, null, null, null)));
        if (!nouvelles.isEmpty()) {
            d.ecritureAlertes().ajouterRemises(tenantId, a.id(), nouvelles);
        }
    }

    /** Les canaux d'avis partent en une relance ; l'appel vocal (ST-G06), en un appel durable par personne. */
    private void relancer(String tenantId, Alerte a, Cascade c, List<Destinataire> vises, String maintenant) {
        List<String> comptes = vises.stream().map(Destinataire::compteAvis).filter(Objects::nonNull).toList();
        Etape e = c.etapeCourante();
        if (!comptes.isEmpty() && !e.codesAvis().isEmpty()) {
            s.echeancier().planifierRelance(tenantId, new EcheancierCascades.Relance(a.id(), c.etape(), comptes, e.codesAvis(), new EcheancierCascades.Message(
                    GestionnaireEnvoiAlertes.evenementDe(a.priorite()), PREFIXE_RELANCE + "[" + a.priorite().code() + "] " + a.titre(), a.corps())), maintenant);
        }
        if (e.appelle()) {
            vises.stream().filter(v -> v.compteAvis() != null).forEach(v -> s.echeancier().planifierAppel(tenantId,
                    new AppelsAlertes.AAppeler(a.id(), c.etape(), v.personId(), v.compteAvis()), maintenant));
        }
    }

    private void tracer(String tenantId, Cascade c, Evenement e, List<String> personnes, String instant, String par) {
        d.ecriture().tracer(tenantId, new Cascades.Trace(c.alerteId(), e, c.etape(), personnes, instant, par));
    }
}

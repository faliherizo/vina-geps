package ca.vina.nexu.ordonnanceur.domaine;

import ca.vina.nexu.partage.temps.Iso;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Les trois questions du domaine — une tâche est-elle échue, se retente-t-elle, quand — répondues
 * sans horloge ni base : {@code maintenant} est une entrée, et un tic se rejoue à l'identique.
 */
public final class Echeances {

    private Echeances() {
    }

    public record Issue(String statut, String echeanceLe, int tentatives) { }

    public static boolean estEchue(TacheDifferee t, String maintenant) {
        return "en_attente".equals(t.statut()) && t.echeanceLe().compareTo(maintenant) <= 0;
    }

    /**
     * Un verrou périmé signale un processus arrêté en pleine tâche : la tâche se reprend. C'est la
     * condition de la reprise après redémarrage — et la raison pour laquelle une tâche fait son
     * travail dans une transaction.
     */
    public static boolean verrouPerime(TacheDifferee t, String maintenant, PolitiqueRetentative p) {
        if (!"en_cours".equals(t.statut()) || t.verrouLe() == null) {
            return false;
        }
        return Instant.parse(maintenant).toEpochMilli() - Instant.parse(t.verrouLe()).toEpochMilli() > p.verrouPerimeMinutes() * 60_000L;
    }

    /** Ce que ce tic doit prendre : les tâches échues, plus celles dont le verrou est périmé. */
    public static List<TacheDifferee> aPrendre(List<TacheDifferee> taches, String maintenant, PolitiqueRetentative p) {
        return taches.stream().filter(t -> estEchue(t, maintenant) || verrouPerime(t, maintenant, p))
                .sorted(Comparator.comparing(TacheDifferee::echeanceLe)).toList();
    }

    /**
     * Ce que devient une tâche après une tentative : faite ; ou remise en attente avec un délai
     * croissant ; ou, au maximum, {@code echouee} et visible — jamais retentée en silence.
     */
    public static Issue apresTentative(TacheDifferee t, boolean reussie, String maintenant, PolitiqueRetentative p) {
        int tentatives = t.tentatives() + 1;
        if (reussie) {
            return new Issue("faite", null, tentatives);
        }
        if (tentatives >= p.tentativesMax()) {
            return new Issue("echouee", null, tentatives);
        }
        int attente = p.attenteMinutes().get(Math.min(tentatives - 1, p.attenteMinutes().size() - 1));
        return new Issue("en_attente", Iso.de(Instant.parse(maintenant).plusMillis(attente * 60_000L)), tentatives);
    }

    /** Une tâche planifiée deux fois pour la même cible ne doit exister qu'une fois. */
    public static String cleDeDedoublonnage(TacheDifferee t) {
        return t.tenantId() + "|" + t.type() + "|" + t.cibleId();
    }

    public static List<String> validerPlanification(String tenantId, String type, String cibleId, String echeanceLe) {
        List<String> erreurs = new ArrayList<>();
        if (tenantId == null || tenantId.isBlank()) {
            erreurs.add("tenantId requis");
        }
        if (type == null || type.isBlank()) {
            erreurs.add("type requis");
        }
        if (cibleId == null || cibleId.isBlank()) {
            erreurs.add("cibleId requis");
        }
        if (echeanceLe == null || !instantValide(echeanceLe)) {
            erreurs.add("echeanceLe doit être un instant ISO");
        }
        return erreurs;
    }

    private static boolean instantValide(String v) {
        try {
            Instant.parse(v);
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }
}

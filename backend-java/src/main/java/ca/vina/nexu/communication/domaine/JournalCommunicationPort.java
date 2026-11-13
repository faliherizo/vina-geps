package ca.vina.nexu.communication.domaine;

/** Port : la trace d'un geste de la communication — qui, quoi, sur quoi, l'état avant et après. */
public interface JournalCommunicationPort {

    record Trace(String acteurId, String action, String cible, Object avant, Object apres) { }

    void consigner(String tenantId, Trace trace);
}

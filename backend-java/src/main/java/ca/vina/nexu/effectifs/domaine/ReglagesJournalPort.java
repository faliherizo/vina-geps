package ca.vina.nexu.effectifs.domaine;

/** Port du réglage du journal (addenda 8, M-10) : l'organisme journalise-t-il les consultations ? Faux par défaut. */
public interface ReglagesJournalPort {

    boolean consultations(String tenantId);

    void poserConsultations(String tenantId, boolean actif);
}

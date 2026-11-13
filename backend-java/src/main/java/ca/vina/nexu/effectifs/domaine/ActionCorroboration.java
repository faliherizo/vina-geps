package ca.vina.nexu.effectifs.domaine;

/**
 * Ce que la feuille fait d'un constat de corroboration (E-RF-13) : l'ignorer, le signaler à qui approuve, ou bloquer la
 * soumission. C'est l'organisme qui en décide — un service sans borne n'a rien à exiger, un service équipé de deux sources
 * peut vouloir qu'elles concordent.
 */
public enum ActionCorroboration {
    IGNORER, SIGNALER, BLOQUER;

    /** L'anomalie d'un constat sous cette action est-elle bloquante ? */
    public boolean bloque() {
        return this == BLOQUER;
    }
}

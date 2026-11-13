package ca.vina.nexu.notifications.domaine;

/** Le sort d'un envoi : parti, en attente, échoué (sans adresse, porteur en panne), désactivé (canal fermé par la personne). */
public enum StatutEnvoi {
    SENT("sent"), QUEUED("queued"), FAILED("failed"), DESACTIVE("desactive");

    private final String code;

    StatutEnvoi(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean parti() {
        return this == SENT;
    }
}

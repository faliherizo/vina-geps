package ca.vina.nexu.communication.domaine;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Par où une personne est jointe : l'ensemble fermé du répertoire d'origine, dans le même ordre. */
public enum CanalReception {
    COURRIEL("courriel", "Courriel"),
    SMS("sms", "Texto (SMS)"),
    PAGETTE("pagette", "Pagette"),
    POUSSEE_MOBILE("poussee_mobile", "Notification mobile"),
    POUSSEE_WEB("poussee_web", "Notification du navigateur"),
    APPEL("appel", "Appel téléphonique");

    private final String code;
    private final String libelle;

    CanalReception(String code, String libelle) {
        this.code = code;
        this.libelle = libelle;
    }

    public String code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    /** La conversion à la frontière : un code inconnu est refusé en nommant ceux qui existent. */
    public static CanalReception de(String code) {
        return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst()
                .orElseThrow(() -> RefusCommunication.invalide("canal inconnu « " + code + " » (attendus : "
                        + Arrays.stream(values()).map(CanalReception::code).collect(Collectors.joining(", ")) + ")"));
    }
}

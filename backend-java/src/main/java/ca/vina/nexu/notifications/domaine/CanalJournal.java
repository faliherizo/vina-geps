package ca.vina.nexu.notifications.domaine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Le canal tel que le journal des notifications le nomme ({@code varpay_notifications.channel}) ; la poussée n'y a pas de ligne. */
public enum CanalJournal {
    EMAIL("email", Canal.COURRIEL), SMS("sms", Canal.TEXTO);

    private final String code;
    private final Canal canal;

    CanalJournal(String code, Canal canal) {
        this.code = code;
        this.canal = canal;
    }

    public String code() {
        return code;
    }

    public static Optional<CanalJournal> de(String code) {
        return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst();
    }

    public static Optional<CanalJournal> pour(Canal canal) {
        return Arrays.stream(values()).filter(c -> c.canal == canal).findFirst();
    }

    public static final List<String> CODES = Arrays.stream(values()).map(CanalJournal::code).toList();
}

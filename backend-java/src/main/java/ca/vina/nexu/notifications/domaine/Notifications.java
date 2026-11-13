package ca.vina.nexu.notifications.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notifications aux professeurs et aux établissements (exigences AIN20260525 #9 et #77) — port de
 * la partie pure de {@code varpay/notifications.ts} : les canaux, les types de destinataire, la
 * substitution {@code {{cle}}} et la validation d'un envoi.
 */
public final class Notifications {

    private Notifications() {
    }

    public static final List<String> CANAUX = CanalJournal.CODES;
    public static final List<String> TYPES_DESTINATAIRE = TypeDestinataire.CODES;
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*\\}\\}");

    /**
     * @param evenement code du catalogue (ADR 0029) : les préférences par événement de la personne s'appliquent ; {@code null} = l'interrupteur général seul
     * @param url       chemin ouvert par la poussée et le texto d'un employé
     */
    public record Demande(String canal, String typeDestinataire, List<String> destinataires, String sujet, String corps, Map<String, Object> variables, String evenement, String url) {

        public Demande(String canal, String typeDestinataire, List<String> destinataires, String sujet, String corps, Map<String, Object> variables) {
            this(canal, typeDestinataire, destinataires, sujet, corps, variables, null, "/admin/notifications");
        }

        /** Le type validé ; {@link #valider} l'a déjà refusé sinon. */
        public TypeDestinataire type() {
            return TypeDestinataire.de(typeDestinataire).orElseThrow(() -> new IllegalArgumentException("recipientType must be one of " + String.join(", ", TYPES_DESTINATAIRE)));
        }

        public CanalJournal canalJournal() {
            return CanalJournal.de(canal).orElseThrow(() -> new IllegalArgumentException("channel must be one of " + String.join(", ", CANAUX)));
        }
    }

    /** Substitue les variables {@code {{cle}}} dans un gabarit ; une variable absente devient vide. */
    public static String rendre(String gabarit, Map<String, Object> variables) {
        Matcher m = VARIABLE.matcher(gabarit);
        StringBuilder r = new StringBuilder();
        while (m.find()) {
            Object v = variables == null ? null : variables.get(m.group(1));
            m.appendReplacement(r, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
        }
        m.appendTail(r);
        return r.toString();
    }

    public static List<String> valider(Demande d) {
        List<String> erreurs = new ArrayList<>();
        if (d.canal() == null || !CANAUX.contains(d.canal())) {
            erreurs.add("channel must be one of " + String.join(", ", CANAUX));
        } else if (d.typeDestinataire() == null || !TYPES_DESTINATAIRE.contains(d.typeDestinataire())) {
            erreurs.add("recipientType must be one of " + String.join(", ", TYPES_DESTINATAIRE));
        } else if (d.destinataires() == null || d.destinataires().isEmpty()) {
            erreurs.add("recipients[] required");
        } else if (d.sujet() == null || d.sujet().trim().isEmpty()) {
            erreurs.add("subject required");
        } else if (d.corps() == null || d.corps().trim().isEmpty()) {
            erreurs.add("body required");
        }
        return erreurs;
    }
}

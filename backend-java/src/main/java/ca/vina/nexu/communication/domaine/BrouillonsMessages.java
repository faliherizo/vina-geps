package ca.vina.nexu.communication.domaine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Les brouillons de message (BF-038) : un par personne et par conversation, jamais lisible d'un tiers. */
public final class BrouillonsMessages {

    public static final int LONGUEUR_MAX = 10_000;

    private BrouillonsMessages() {
    }

    public record Brouillon(String conversationId, String corps, String modifieLe) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("conversationId", conversationId);
            m.put("corps", corps);
            m.put("modifieLe", modifieLe);
            return m;
        }
    }

    /** Un brouillon vide est licite (il efface) ; absent, ou trop long, il ne l'est pas. */
    public static List<String> valider(String corps) {
        List<String> erreurs = new ArrayList<>();
        if (corps == null) {
            erreurs.add("corps requis");
        } else if (corps.length() > LONGUEUR_MAX) {
            erreurs.add("brouillon trop long (10 000 caractères au maximum)");
        }
        return erreurs;
    }
}

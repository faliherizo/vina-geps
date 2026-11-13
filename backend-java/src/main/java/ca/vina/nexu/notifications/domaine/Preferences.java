package ca.vina.nexu.notifications.domaine;

import ca.vina.nexu.notifications.domaine.Evenements.Canaux;
import ca.vina.nexu.notifications.domaine.Evenements.Evenement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Les canaux qu'une personne a choisis (devis 2, objectif 6 ; ADR 0029) — port de
 * {@code notifications/preferences.ts} : courriel, texto, notification poussée, en général et
 * <strong>par événement</strong>. Par défaut tout est ouvert ; un événement non réglé prend les
 * défauts du catalogue. Au moins un canal écrit reste actif en général, et — effectif, c'est-à-dire
 * ouvert pour l'événement <em>et</em> en général — pour chaque avis essentiel.
 */
public record Preferences(boolean courriel, boolean texto, boolean poussee, Map<String, Canaux> evenements, String courrielPersonnel, String updatedAt) {

    /** Sans adresse personnelle : la seule adresse institutionnelle. */
    public Preferences(boolean courriel, boolean texto, boolean poussee, Map<String, Canaux> evenements, String updatedAt) {
        this(courriel, texto, poussee, evenements, null, updatedAt);
    }

    public static final Preferences PAR_DEFAUT = new Preferences(true, true, true, Map.of(), null);
    private static final Pattern COURRIEL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String CLE_COURRIEL_PERSONNEL = "courrielPersonnel";
    public static final List<String> CANAUX = Evenements.CANAUX;

    public boolean ouvert(Canal canal) {
        return generaux().ouvert(canal);
    }

    /** Le code d'un canal, tel que la frontière le reçoit ; {@link IllegalArgumentException} nommée s'il est inconnu. */
    public boolean ouvert(String canal) {
        return ouvert(Canal.de(canal).orElseThrow(() -> new IllegalArgumentException("canal inconnu : " + canal)));
    }

    public Canaux generaux() {
        return new Canaux(courriel, texto, poussee);
    }

    /** Le canal est-il ouvert pour cet événement : en général <em>et</em> pour l'événement (défaut du catalogue s'il n'est pas réglé) ? */
    public boolean ouvert(Canal canal, String evenement) {
        if (!ouvert(canal)) {
            return false;
        }
        return Evenements.parCode(evenement == null ? "" : evenement).map(e -> canauxDe(e).ouvert(canal)).orElse(true);
    }

    public boolean ouvert(String canal, String evenement) {
        return ouvert(Canal.de(canal).orElseThrow(() -> new IllegalArgumentException("canal inconnu : " + canal)), evenement);
    }

    public Canaux canauxDe(Evenement e) {
        return evenements.getOrDefault(e.code(), e.defauts());
    }

    /** Les avis essentiels privés de tout canal écrit effectif, nommés. */
    public List<String> refusEssentiels() {
        List<String> erreurs = new ArrayList<>();
        for (Evenement e : Evenements.TOUS) {
            if (e.essentiel() && !ouvert(Canal.COURRIEL, e.code()) && !ouvert(Canal.TEXTO, e.code())) {
                erreurs.add(e.libelle() + " : avis essentiel, gardez le courriel ou le texto ouvert");
            }
        }
        return erreurs;
    }

    /**
     * Le correctif de l'écran appliqué à l'existant ; une clé absente garde sa valeur, une clé d'une autre forme est
     * refusée ; par événement, chaque code fourni remplace son réglage (complété par les défauts).
     */
    @SuppressWarnings("unchecked")
    public Preferences fusionner(Map<String, Object> correctif, String instant) {
        List<String> erreurs = new ArrayList<>();
        Canaux generaux = generaux();
        for (Canal canal : Canal.values()) {
            Object v = correctif.get(canal.code());
            if (v == null) {
                continue;
            }
            if (v instanceof Boolean b) {
                generaux = generaux.avec(canal, b);
            } else {
                erreurs.add(canal.code() + " : vrai ou faux attendu");
            }
        }
        if (!generaux.ecritOuvert()) {
            erreurs.add("au moins un canal écrit (courriel ou texto) doit rester actif");
        }
        erreurs.addAll(Evenements.refusReglages(correctif.get("evenements")));
        // L'adresse personnelle (CSSMB 21, ADR 0039) : une adresse valide, ou vide pour la retirer ; absente du correctif, elle reste.
        String adresse = courrielPersonnel;
        if (correctif.containsKey(CLE_COURRIEL_PERSONNEL)) {
            Object v = correctif.get(CLE_COURRIEL_PERSONNEL);
            if (v == null || (v instanceof String s && s.isBlank())) {
                adresse = null;
            } else if (v instanceof String s && COURRIEL.matcher(s.trim()).matches()) {
                adresse = s.trim().toLowerCase(Locale.ROOT);
            } else {
                erreurs.add("courrielPersonnel : une adresse de courriel valide, ou vide");
            }
        }
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        Map<String, Canaux> regles = new LinkedHashMap<>(evenements);
        if (correctif.get("evenements") instanceof Map<?, ?> carte) {
            for (Map.Entry<?, ?> entree : carte.entrySet()) {
                Evenement e = Evenements.parCode(String.valueOf(entree.getKey())).orElseThrow();
                regles.put(e.code(), Evenements.completer(e, (Map<String, Object>) entree.getValue()));
            }
        }
        Preferences fusion = new Preferences(generaux.courriel(), generaux.texto(), generaux.poussee(), Map.copyOf(regles), adresse, instant);
        List<String> essentiels = fusion.refusEssentiels();
        if (!essentiels.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", essentiels));
        }
        return fusion;
    }

    public Map<String, Object> vue() {
        Map<String, Object> m = new LinkedHashMap<>(generaux().vue());
        Map<String, Object> ev = new LinkedHashMap<>();
        evenements.forEach((code, c) -> ev.put(code, c.vue()));
        m.put("evenements", ev);
        m.put("courrielPersonnel", courrielPersonnel);
        m.put("updatedAt", updatedAt);
        return m;
    }
}

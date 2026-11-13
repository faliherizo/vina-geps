package ca.vina.nexu.partage.journal;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <strong>Masquage</strong> (ADR 0041, JOURNALISATION.md § 5) — les règles pures qui gardent les
 * secrets et les renseignements personnels hors du journal, les mêmes qu'au Node : on journalise
 * des identifiants et des faits, jamais des contenus.
 */
public final class Masquage {

    private Masquage() {
    }

    private static final Pattern UUID = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
    /** Un segment qui ressemble à un jeton : long, opaque, et pas un UUID (les UUID sont des identifiants d'entités). */
    private static final Pattern JETON = Pattern.compile("^[A-Za-z0-9_-]{22,}$");
    /** Sous ces préfixes, le dernier segment est un jeton quelle que soit sa forme (liens publics, tiers de démonstration). */
    private static final List<String> PREFIXES_A_JETON = List.of("/public/", "/sign/", "/carriere/", "/disponibilites/", "/mock/");
    private static final String SEGMENT_JETON = "{jeton}";
    private static final int CHIFFRES_MINIMUM_TELEPHONE = 7;
    private static final int CHIFFRES_GARDES = 4;

    /** Le chemin tel qu'il se journalise : segments-jetons → {jeton}, noms des paramètres seulement. */
    public static String masquerChemin(String chemin, String requete) {
        String[] segments = chemin.split("/", -1);
        boolean sousPrefixe = PREFIXES_A_JETON.stream().anyMatch(chemin::startsWith) && segments.length > 2;
        StringBuilder sortie = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String s = segments[i];
            boolean dernier = i == segments.length - 1;
            if (!s.isEmpty() && ((sousPrefixe && dernier) || (JETON.matcher(s).matches() && !UUID.matcher(s).matches()))) {
                s = SEGMENT_JETON;
            }
            sortie.append(i == 0 ? "" : "/").append(s);
        }
        if (requete != null && !requete.isEmpty()) {
            Set<String> noms = new LinkedHashSet<>();
            for (String p : requete.split("&")) {
                String nom = p.split("=", 2)[0];
                if (!nom.isEmpty()) {
                    noms.add(nom);
                }
            }
            if (!noms.isEmpty()) {
                sortie.append('?').append(String.join("&", noms));
            }
        }
        return sortie.toString();
    }

    /** Un courriel ou un téléphone qu'on doit citer (un refus de transport) : le début seulement. */
    public static String masquer(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return "—";
        }
        String v = valeur.trim();
        int arobase = v.indexOf('@');
        if (arobase > 0) {
            return v.charAt(0) + "***@" + v.substring(arobase + 1);
        }
        String chiffres = v.replaceAll("\\D", "");
        if (chiffres.length() >= CHIFFRES_MINIMUM_TELEPHONE) {
            return "***" + chiffres.substring(chiffres.length() - CHIFFRES_GARDES);
        }
        return v.length() <= 2 ? "***" : v.substring(0, 2) + "***";
    }

    /** Le libellé fixe d'un 4xx : on ne journalise jamais le message, qui cite souvent la saisie. */
    public static String libelleDuStatut(int statut) {
        if (statut < 400 || statut >= 500) {
            return null;
        }
        return switch (statut) {
            case 401 -> "non authentifie";
            case 403 -> "refuse";
            case 404 -> "introuvable";
            case 409 -> "conflit";
            case 413 -> "trop volumineux";
            case 429 -> "trop de requetes";
            default -> "validation";
        };
    }

    private static final Set<String> LECTURES = Arrays.stream(new String[] {"GET", "HEAD", "OPTIONS"}).collect(Collectors.toSet());

    /** Le niveau d'une ligne http : 5xx = error, autre échec = info, lecture réussie = debug, écriture réussie = info. */
    public static Niveau niveauDeLaRequete(String methode, int statut) {
        if (statut >= 500) {
            return Niveau.ERROR;
        }
        if (statut >= 400) {
            return Niveau.INFO;
        }
        return LECTURES.contains(methode) ? Niveau.DEBUG : Niveau.INFO;
    }

    public static boolean estMutante(String methode) {
        return !LECTURES.contains(methode);
    }
}

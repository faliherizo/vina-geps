package ca.vina.nexu.auth.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Politique de mot de passe élevé — port de {@code backend/src/auth/passwordPolicy.ts}.
 *
 * <p>Les manquements sont rendus dans l'ordre et avec les mots du Node : le front les affiche
 * tels quels, et un essai de contrat les compare caractère pour caractère.
 */
public final class PolitiqueMotDePasse {

    public static final int LONGUEUR_MINIMALE = 12;

    private static final Set<String> COURANTS = Set.of(
            "password", "passw0rd", "motdepasse", "123456", "12345678", "123456789", "azerty", "azerty123",
            "qwerty", "qwerty123", "admin", "administrateur", "superadmin", "welcome", "bienvenue",
            "soleil", "iloveyou", "letmein", "changeme", "secret", "secret1", "abc123", "111111", "000000");

    private static final Pattern MINUSCULE = Pattern.compile("[a-z]");
    private static final Pattern MAJUSCULE = Pattern.compile("[A-Z]");
    private static final Pattern CHIFFRE = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern ESPACE = Pattern.compile("\\s");

    private PolitiqueMotDePasse() {
    }

    /** Liste des manquements, vide si le mot de passe est conforme. */
    public static List<String> manquements(String motDePasse, String nomUtilisateur) {
        String pw = motDePasse == null ? "" : motDePasse;
        List<String> manques = new ArrayList<>();
        if (pw.length() < LONGUEUR_MINIMALE) {
            manques.add("au moins " + LONGUEUR_MINIMALE + " caractères");
        }
        if (!MINUSCULE.matcher(pw).find()) {
            manques.add("une lettre minuscule");
        }
        if (!MAJUSCULE.matcher(pw).find()) {
            manques.add("une lettre majuscule");
        }
        if (!CHIFFRE.matcher(pw).find()) {
            manques.add("un chiffre");
        }
        if (!SPECIAL.matcher(pw).find()) {
            manques.add("un caractère spécial");
        }
        if (ESPACE.matcher(pw).find()) {
            manques.add("aucun espace");
        }
        String nom = (nomUtilisateur == null ? "" : nomUtilisateur).trim().toLowerCase(Locale.ROOT);
        if (nom.length() >= 3 && pw.toLowerCase(Locale.ROOT).contains(nom)) {
            manques.add("ne pas contenir le nom d'utilisateur");
        }
        if (COURANTS.contains(pw.toLowerCase(Locale.ROOT))) {
            manques.add("ne pas être un mot de passe courant");
        }
        return manques;
    }

    /** Le message du Node, mot pour mot. */
    public static String message(List<String> manquements) {
        return "mot de passe trop faible — il doit contenir : " + String.join(", ", manquements);
    }

    /**
     * Un mot de passe provisoire fort ({@code generateTempPassword}) : une minuscule, une majuscule,
     * un chiffre et un caractère spécial garantis, sans les caractères qui se confondent (l, 1, I, O, 0),
     * puis mélangé pour que les classes garanties ne soient pas toujours devant. L'aléa est injecté.
     */
    public static String motDePasseProvisoire(int longueur, java.util.random.RandomGenerator alea) {
        String minuscules = "abcdefghijkmnpqrstuvwxyz";
        String majuscules = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String chiffres = "23456789";
        String speciaux = "!@#$%^&*-_=+?";
        String tous = minuscules + majuscules + chiffres + speciaux;
        List<Character> caracteres = new ArrayList<>(List.of(
                minuscules.charAt(alea.nextInt(minuscules.length())), majuscules.charAt(alea.nextInt(majuscules.length())),
                chiffres.charAt(alea.nextInt(chiffres.length())), speciaux.charAt(alea.nextInt(speciaux.length()))));
        while (caracteres.size() < Math.max(longueur, LONGUEUR_MINIMALE)) {
            caracteres.add(tous.charAt(alea.nextInt(tous.length())));
        }
        for (int i = caracteres.size() - 1; i > 0; i--) {
            int j = alea.nextInt(i + 1);
            Character t = caracteres.get(i);
            caracteres.set(i, caracteres.get(j));
            caracteres.set(j, t);
        }
        StringBuilder b = new StringBuilder();
        caracteres.forEach(b::append);
        return b.toString();
    }

    /** Lève {@link MotDePasseFaibleException} si le mot de passe n'est pas conforme. */
    public static void exiger(String motDePasse, String nomUtilisateur) {
        List<String> manques = manquements(motDePasse, nomUtilisateur);
        if (!manques.isEmpty()) {
            throw new MotDePasseFaibleException(manques);
        }
    }

    public static final class MotDePasseFaibleException extends IllegalArgumentException {
        private final List<String> manquements;

        public MotDePasseFaibleException(List<String> manquements) {
            super(message(manquements));
            this.manquements = List.copyOf(manquements);
        }

        public List<String> manquements() {
            return manquements;
        }
    }
}

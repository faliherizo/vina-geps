package ca.vina.nexu.organisme.domaine;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Les règles d'alerte (P-TN-10) : leurs destinataires, et le texte d'une alerte. Jumeau des fonctions du Node. */
public final class Alertes {

    private static final int MAXIMUM = 10;
    // \s de JavaScript couvre les espaces Unicode : même classe ici, sans quoi une espace insécable passerait.
    private static final Pattern COURRIEL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String VIDE = "vide";

    private Alertes() {
    }

    /** Le texte d'une alerte. */
    public record TexteAlerte(String sujet, String texte) {
    }

    /** De 1 à 10 adresses, ramenées en minuscules, sans doublon, dans l'ordre reçu. */
    public static List<String> destinatairesDe(Object v) {
        if (!(v instanceof List<?> l) || l.isEmpty() || l.size() > MAXIMUM) {
            throw new OptionInvalideException("« destinataires » : de 1 à " + MAXIMUM + " adresses courriel");
        }
        List<String> adresses = l.stream().map(x -> x instanceof String s ? s.strip().toLowerCase(Locale.ROOT) : "").toList();
        List<String> faux = adresses.stream().filter(a -> !COURRIEL.matcher(a).matches()).map(a -> a.isEmpty() ? VIDE : a).toList();
        if (!faux.isEmpty()) {
            throw new OptionInvalideException("« destinataires » : adresse invalide (" + String.join(", ", faux) + ")");
        }
        return List.copyOf(new LinkedHashSet<>(adresses));
    }

    /** Le texte d'une alerte : ce qui s'est passé, qui, quand — jamais le contenu téléchargé. */
    public static TexteAlerte texte(EvenementAlerte e, Circonstances d) {
        return new TexteAlerte("[Alerte] " + e.libelle(), e.libelle() + ".\n\nQui : " + d.qui() + "\nQuand : " + d.quand() + "\nQuoi : " + d.quoi()
                + "\n\nCette alerte suit une règle définie par l'administration de votre organisme.");
    }
}

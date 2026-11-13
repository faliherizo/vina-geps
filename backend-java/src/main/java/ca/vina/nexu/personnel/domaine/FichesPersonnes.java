package ca.vina.nexu.personnel.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <b>La fiche d'une personne</b>, telle que ce produit la tient : un nom, de quoi la joindre, un titre.
 * C'est ce qu'un avis « par le nom » et un champ « personne » ont besoin de trouver. La fiche ne porte
 * pas la paie : ce produit ne la fait pas.
 */
public final class FichesPersonnes {

    private static final Pattern COURRIEL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private FichesPersonnes() {
    }

    /** Ce que l'appelant demande ; {@code embaucheLe} absent vaut aujourd'hui. */
    public record Demande(String prenom, String nom, String courriel, String telephone, String titre, String embaucheLe) { }

    public record Fiche(String id, String prenom, String nom, String courriel, String telephone, String titre, String embaucheLe) { }

    /** Une demande refusée : chaque motif est nommé, pour être corrigé d'un coup. */
    public static final class FicheInvalideException extends RuntimeException {
        public FicheInvalideException(List<String> motifs) {
            super(String.join(" · ", motifs));
        }
    }

    /** Port d'écriture : ranger une fiche nouvelle, ou remplacer une fiche existante. */
    public interface Ecriture {
        void creer(String tenantId, Fiche fiche);

        boolean modifier(String tenantId, Fiche fiche);
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    public static List<String> valider(Demande d) {
        List<String> motifs = new ArrayList<>();
        if (vide(d.prenom())) {
            motifs.add("prénom requis");
        }
        if (vide(d.nom())) {
            motifs.add("nom requis");
        }
        if (!vide(d.courriel()) && !COURRIEL.matcher(d.courriel().trim()).matches()) {
            motifs.add("courriel invalide");
        }
        if (!vide(d.embaucheLe()) && !DATE.matcher(d.embaucheLe()).matches()) {
            motifs.add("date d'embauche attendue au format AAAA-MM-JJ");
        }
        return motifs;
    }

    /** La fiche rangée : textes rognés, champs vides rendus nuls, date d'embauche par défaut. */
    public static Fiche fiche(String id, Demande d, String aujourdhui) {
        List<String> motifs = valider(d);
        if (!motifs.isEmpty()) {
            throw new FicheInvalideException(motifs);
        }
        return new Fiche(id, d.prenom().trim(), d.nom().trim(), net(d.courriel()), net(d.telephone()), net(d.titre()),
                vide(d.embaucheLe()) ? aujourdhui : d.embaucheLe());
    }

    /**
     * La demande tirée du nom d'une ressource d'effectifs (« Julie Roy ») : le dernier mot est le nom,
     * ce qui le précède le prénom. Un nom d'un seul mot sert aux deux — la fiche reste trouvable par
     * ce mot, et l'administration la corrige ensuite.
     */
    public static Demande depuisNomComplet(String nomComplet, String courriel) {
        String n = nomComplet == null ? "" : nomComplet.trim().replaceAll("\\s+", " ");
        int espace = n.lastIndexOf(' ');
        String prenom = espace < 0 ? n : n.substring(0, espace);
        String nom = espace < 0 ? n : n.substring(espace + 1);
        return new Demande(prenom, nom, courriel, null, null, null);
    }

    private static String net(String s) {
        return vide(s) ? null : s.trim();
    }
}

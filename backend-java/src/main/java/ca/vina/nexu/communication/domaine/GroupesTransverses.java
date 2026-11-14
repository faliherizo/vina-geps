package ca.vina.nexu.communication.domaine;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <strong>Les groupes transverses</strong> (FX-AO-20260810, ST-G09, critère 27) : des groupes de spécialité, de garde ou
 * de comité, <em>hors</em> des unités, que l'organisme forme lui-même. Ils servent à la messagerie, aux listes de
 * distribution et aux alertes comme les unités, sans exiger le module d'effectifs.
 *
 * <p>L'appartenance est <strong>datée</strong> : une garde de fin de semaine commence et finit ; une personne n'est
 * membre qu'entre ses dates. Un modérateur tient la liste de son groupe. Un groupe se désactive, jamais ne s'efface.
 */
public final class GroupesTransverses {

    /** Le préfixe de leurs identifiants : il les distingue des codes d'unité dans un même port de groupes. */
    public static final String PREFIXE = "gt-";
    public static final int NOM_MAX = 80;
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private GroupesTransverses() {
    }

    /** Ce que le groupe rassemble ; un ensemble fermé. */
    public enum Nature {
        SPECIALITE("specialite"), GARDE("garde"), COMITE("comite");

        private final String code;

        Nature(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Optional<Nature> de(String code) {
            return Arrays.stream(values()).filter(n -> n.code.equals(code)).findFirst();
        }
    }

    public record Groupe(String id, String nom, Nature nature, boolean actif) {

        public Groupe avec(String nouveauNom, boolean a) {
            return new Groupe(id, GroupesTransverses.nom(nouveauNom), nature, a);
        }
    }

    /** Une appartenance ; {@code depuis} et {@code jusqua} nuls : sans borne de ce côté. */
    public record Appartenance(String personId, String nom, String depuis, String jusqua, boolean moderateur) {

        /** Membre ce jour-là, bornes comprises. */
        public boolean active(String jour) {
            return (depuis == null || jour.compareTo(depuis) >= 0) && (jusqua == null || jour.compareTo(jusqua) <= 0);
        }
    }

    public static Groupe nouveau(String id, String nom, String nature) {
        Nature n = Nature.de(nature == null ? "" : nature)
                .orElseThrow(() -> RefusCommunication.invalide("« nature » : specialite, garde ou comite"));
        return new Groupe(PREFIXE + id, nom(nom), n, true);
    }

    static String nom(String brut) {
        String n = brut == null ? "" : brut.strip();
        if (n.isEmpty() || n.length() > NOM_MAX) {
            throw RefusCommunication.invalide("« nom » : 1 à " + NOM_MAX + " caractères");
        }
        return n;
    }

    /** Une appartenance validée : dates AAAA-MM-JJ, la fin jamais avant le début. */
    public static Appartenance appartenance(String personId, String nom, String depuis, String jusqua, boolean moderateur) {
        String d = date("depuis", depuis);
        String j = date("jusqua", jusqua);
        if (d != null && j != null && j.compareTo(d) < 0) {
            throw RefusCommunication.invalide("« jusqua » : la fin ne précède pas le début");
        }
        return new Appartenance(personId, nom, d, j, moderateur);
    }

    private static String date(String champ, String brut) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        if (!DATE.matcher(brut.strip()).matches()) {
            throw RefusCommunication.invalide("« " + champ + " » : une date AAAA-MM-JJ");
        }
        return brut.strip();
    }

    public static boolean estTransverse(String groupeId) {
        return groupeId != null && groupeId.startsWith(PREFIXE);
    }
}

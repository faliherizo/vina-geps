package ca.vina.nexu.effectifs.domaine;

/**
 * Une version datée d'une règle : {@code effectifDepuis ≤ date < effectifJusqua}, {@code effectifJusqua} nul
 * = sans fin. C'est une {@link Referentiels.Version} : elle se remplace et se ferme selon les mêmes gardes
 * de non-rétroactivité que les référentiels.
 */
public record VersionRegle(String id, Regle regle, int version, ContenuRegle contenu, String effectifDepuis, String effectifJusqua)
        implements Referentiels.Version {

    public static final String BROUILLON = "brouillon";
    /** Une date antérieure à toute autre : le brouillon d'aperçu est en vigueur à la date de chaque dossier. */
    private static final String ORIGINE = "0000-01-01";

    /** Une règle d'aperçu : d'organisme, sans fin, visant tous les dossiers. */
    public static VersionRegle brouillon(ContexteRegle contexte, ContenuRegle contenu) {
        return new VersionRegle(BROUILLON, new Regle(BROUILLON, contexte, BROUILLON, PorteeRegle.ORGANISME, null), 0, contenu, ORIGINE, null);
    }

    public boolean enVigueurLe(String date) {
        return effectifDepuis.compareTo(date) <= 0 && (effectifJusqua == null || date.compareTo(effectifJusqua) < 0);
    }
}

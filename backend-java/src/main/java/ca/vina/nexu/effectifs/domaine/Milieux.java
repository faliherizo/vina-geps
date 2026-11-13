package ca.vina.nexu.effectifs.domaine;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <strong>Milieux physiques et preuves de présence exigées</strong> — E-GN-17, E-GP-7, E-GP-11, E-GP-12 ; le
 * pur de {@code backend/src/effectifs/socle/milieux.ts}. Les coordonnées se saisissent en champs : une
 * carte peut situer, jamais servir seule à saisir (WCAG 2.5.7).
 */
public final class Milieux {

    public static final int RAYON_MIN_METRES = 10;
    public static final int RAYON_MAX_METRES = 5000;
    private static final double RAYON_TERRE_METRES = 6_371_000;
    private static final Pattern CIDR = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})/(\\d{1,2})$");
    private static final String PLAGES = "plages";

    private Milieux() {
    }

    public enum Mecanisme { GEOREPERAGE, CODE_QR_DE_LIEU, RESEAU, BORNE, SOURCE_EXTERNE }

    public record Coordonnees(double latitude, double longitude, int rayonMetres) { }

    public record Preuve(Mecanisme mecanisme, boolean exige, Map<String, Object> parametres) { }

    public static Mecanisme mecanismeDe(Object v) {
        return Arrays.stream(Mecanisme.values()).filter(m -> m.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("mécanisme de preuve inconnu : « " + v + " » (attendu : "
                        + Arrays.stream(Mecanisme.values()).map(Enum::name).collect(Collectors.joining(", ")) + ")"));
    }

    private static Double nombre(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static boolean fourni(Object v) {
        return v != null && !"".equals(v);
    }

    /** Valide une position et son rayon ; les trois vont ensemble ou pas du tout. */
    public static Coordonnees coordonneesDe(Map<String, Object> entree) {
        Object lat = entree.get("latitude");
        Object lon = entree.get("longitude");
        Object rayon = entree.get("rayonMetres");
        long fournis = Stream.of(lat, lon, rayon).filter(Milieux::fourni).count();
        if (fournis == 0) {
            return null;
        }
        if (fournis != 3) {
            throw new EffectifsInvalideException("latitude, longitude et rayon se saisissent ensemble");
        }
        double latitude = nombre(lat);
        double longitude = nombre(lon);
        double r = nombre(rayon);
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new EffectifsInvalideException("latitude entre -90 et 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new EffectifsInvalideException("longitude entre -180 et 180");
        }
        if (!Double.isFinite(r) || r != Math.rint(r) || r < RAYON_MIN_METRES || r > RAYON_MAX_METRES) {
            throw new EffectifsInvalideException("rayon entier entre " + RAYON_MIN_METRES + " et " + RAYON_MAX_METRES + " mètres");
        }
        return new Coordonnees(latitude, longitude, (int) r);
    }

    /** Distance au sol entre deux points (haversine), arrondie au mètre. */
    public static long distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return Math.round(2 * RAYON_TERRE_METRES * Math.asin(Math.sqrt(h)));
    }

    /** Valide une plage réseau IPv4 en notation CIDR (« 10.20.0.0/16 »). */
    public static String cidrValide(String v) {
        Matcher m = CIDR.matcher(v.trim());
        boolean ok = m.matches() && Stream.of(1, 2, 3, 4).allMatch(i -> Integer.parseInt(m.group(i)) <= 255) && Integer.parseInt(m.group(5)) <= 32;
        if (!ok) {
            throw new EffectifsInvalideException("plage réseau invalide : « " + v + " » (attendu : a.b.c.d/nn)");
        }
        return v.trim();
    }

    private static long versEntier(String ip) {
        long acc = 0;
        for (String o : ip.split("\\.")) {
            acc = acc * 256 + Long.parseLong(o);
        }
        return acc;
    }

    /** Une adresse IPv4 appartient-elle à une plage CIDR ? */
    public static boolean dansPlage(String ip, String cidr) {
        String[] p = cidr.split("/");
        long taille = 1L << (32 - Integer.parseInt(p[1]));
        long debut = versEntier(p[0]) / taille * taille;
        long valeur = versEntier(ip);
        return valeur >= debut && valeur < debut + taille;
    }

    /**
     * Valide les preuves d'un milieu. Le géorepérage exige des coordonnées ; le réseau, au moins une
     * plage ; le code QR, rien (son secret est tiré par le serveur, jamais saisi).
     */
    @SuppressWarnings("unchecked")
    public static List<Preuve> preuvesValides(List<Map<String, Object>> entrees, Coordonnees coordonnees) {
        Set<Mecanisme> vus = EnumSet.noneOf(Mecanisme.class);
        return entrees.stream().map(e -> {
            Mecanisme mecanisme = mecanismeDe(e.get("mecanisme"));
            if (!vus.add(mecanisme)) {
                throw new EffectifsInvalideException("mécanisme en double : " + mecanisme);
            }
            boolean exige = Boolean.TRUE.equals(e.get("exige"));
            Map<String, Object> brut = e.get("parametres") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            if (mecanisme == Mecanisme.GEOREPERAGE && coordonnees == null) {
                throw new EffectifsInvalideException("le géorepérage exige la latitude, la longitude et le rayon du milieu");
            }
            if (mecanisme == Mecanisme.RESEAU) {
                List<String> plages = brut.get(PLAGES) instanceof Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of();
                if (plages.isEmpty()) {
                    throw new EffectifsInvalideException("la preuve par réseau exige au moins une plage (a.b.c.d/nn)");
                }
                return new Preuve(mecanisme, exige, Map.<String, Object>of(PLAGES, plages.stream().map(Milieux::cidrValide).toList()));
            }
            return new Preuve(mecanisme, exige, Map.<String, Object>of());
        }).toList();
    }
}

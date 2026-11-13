package ca.vina.nexu.partage.securite;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * <strong>Le tenant vient de l'hôte, jamais du client</strong> (ADR 0033, {@code DEPLOIEMENT-NEXUS-VINAITSOLUTION.md}).
 *
 * <p>Deux modes, choisis par {@code TENANT_RESOLUTION} : {@code selecteur} (développement, essais —
 * l'écran de connexion propose les établissements) et {@code sous_domaine} (production —
 * {@code cssdc.nexus-vinaitsolution.com} <em>est</em> l'établissement CSSDC). Le serveur lit l'hôte
 * ({@code X-Forwarded-Host} posé par nginx, sinon {@code Host}), en tire le slug ; {@code admin.} est
 * réservé aux comptes globaux, la racine et {@code www.} à la vitrine.
 *
 * <p>Pur : la lecture du dépôt des établissements est l'affaire de {@link HoteEtablissement}. La
 * configuration se valide <strong>au démarrage</strong> — un mode {@code sous_domaine} sans racine
 * arrête l'application, jamais la première connexion.
 */
public final class ResolutionHote {

    public static final String SOUS_DOMAINE_ADMIN = "admin";
    public static final String RESERVES_PAR_DEFAUT = "www,admin,api,vitrine,app";
    private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String HOST = "Host";

    private ResolutionHote() {
    }

    /** Le mode, la racine ({@code nexus-vinaitsolution.com}) et les sous-domaines qui ne sont pas des établissements. */
    public record Configuration(ModeResolution mode, String domaineRacine, Set<String> reserves) {

        public Configuration {
            reserves = Set.copyOf(reserves);
        }

        /** Les valeurs telles que l'environnement les donne ; refuse ce qui ne peut pas servir. */
        public static Configuration depuis(String mode, String racine, String reservesCsv) {
            String codeMode = mode == null || mode.isBlank() ? ModeResolution.SELECTEUR.code() : mode.trim();
            ModeResolution m = ModeResolution.de(codeMode)
                    .orElseThrow(() -> new IllegalStateException("TENANT_RESOLUTION inconnu : « " + codeMode + " » (admis : selecteur, sous_domaine)"));
            String r = racine == null ? "" : racine.trim().toLowerCase(Locale.ROOT).replaceAll("^\\.+|\\.+$", "");
            if (m == ModeResolution.SOUS_DOMAINE && r.isEmpty()) {
                throw new IllegalStateException("TENANT_RESOLUTION=sous_domaine exige DOMAINE_RACINE (ex. nexus-vinaitsolution.com)");
            }
            Set<String> reserves = new LinkedHashSet<>();
            Arrays.stream((reservesCsv == null || reservesCsv.isBlank() ? RESERVES_PAR_DEFAUT : reservesCsv).split(","))
                    .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).forEach(reserves::add);
            reserves.add(SOUS_DOMAINE_ADMIN);
            return new Configuration(m, r.isEmpty() ? null : r, reserves);
        }

        public boolean parSousDomaine() {
            return mode == ModeResolution.SOUS_DOMAINE && domaineRacine != null;
        }

        /**
         * L'URL publique d'un établissement pour les liens sortants : {@code https://{slug}.{racine}} —
         * l'hôte des comptes globaux sans slug ; hors sous-domaine, l'adresse commune donnée.
         */
        public String urlPublique(String slug, String commune) {
            if (!parSousDomaine()) {
                return commune == null ? "" : commune;
            }
            return "https://" + (slug == null || slug.isBlank() ? SOUS_DOMAINE_ADMIN : slug) + "." + domaineRacine;
        }
    }

    /** Ce que dit l'hôte : la racine (vitrine), un sous-domaine réservé, un slug d'établissement, ou rien de connu. */
    public sealed interface Genre {
        record Selecteur() implements Genre { }
        record Racine() implements Genre { }
        record Reserve(String nom) implements Genre { }
        record HorsDomaine(String hote) implements Genre { }
        record Etablissement(String slug) implements Genre { }
    }

    /** L'hôte tel que le proxy le transmet : {@code X-Forwarded-Host} (première valeur) sinon {@code Host}, sans port, en minuscules. */
    public static String hoteDe(String xForwardedHost, String host) {
        String brut = xForwardedHost != null && !xForwardedHost.isBlank() ? xForwardedHost : host == null ? "" : host;
        return brut.split(",")[0].trim().toLowerCase(Locale.ROOT).replaceAll(":\\d+$", "");
    }

    /** Les deux en-têtes lus sur une requête, dans l'ordre où le proxy les pose. */
    public static String hoteDe(jakarta.servlet.http.HttpServletRequest requete) {
        return hoteDe(requete.getHeader(X_FORWARDED_HOST), requete.getHeader(HOST));
    }

    public static Genre genreDe(String hote, Configuration c) {
        if (!c.parSousDomaine()) {
            return new Genre.Selecteur();
        }
        if (hote.equals(c.domaineRacine())) {
            return new Genre.Racine();
        }
        String suffixe = "." + c.domaineRacine();
        if (!hote.endsWith(suffixe)) {
            return new Genre.HorsDomaine(hote);
        }
        String prefixe = hote.substring(0, hote.length() - suffixe.length());
        if (prefixe.isEmpty() || prefixe.contains(".")) {
            return new Genre.HorsDomaine(hote);
        }
        return c.reserves().contains(prefixe) ? new Genre.Reserve(prefixe) : new Genre.Etablissement(prefixe);
    }
}

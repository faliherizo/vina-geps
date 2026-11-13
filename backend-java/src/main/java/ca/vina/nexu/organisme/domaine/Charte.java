package ca.vina.nexu.organisme.domaine;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <strong>La charte graphique d'un organisme</strong> (AO 26-015-DO, P-TN-9) : son nom affiché, deux couleurs
 * et un logo. Jumeau de {@code charteDe} du Node : une charte qui rendrait un texte illisible est refusée
 * <strong>à l'enregistrement</strong>, avec le chiffre mesuré — jamais découverte à l'écran par un lecteur.
 *
 * @param logo une image matricielle en data URL, ou {@code null}
 */
public record Charte(String nomAffiche, String couleurPrincipale, String couleurLaterale, String logo) {

    /** Un logo : 200 Ko au plus. */
    public static final int LOGO_MAX_OCTETS = 200_000;
    private static final int OCTETS_PAR_KO = 1000;
    private static final int NOM_MAX = 60;
    private static final int OCTETS_PAR_QUATRE_CARACTERES = 3;
    private static final int CARACTERES_PAR_BLOC = 4;
    // Pas de SVG : il peut porter du script, et la charte s'affiche sur tous les écrans de l'organisme.
    private static final Pattern LOGO = Pattern.compile("^data:image/(png|jpeg|webp);base64,([A-Za-z0-9+/]+={0,2})$");
    private static final double FACTEUR_SURVOL = 0.85;
    private static final double FACTEUR_DOUX = 1.92;

    /** La valeur d'une des deux couleurs. */
    public String couleur(CouleurCharte c) {
        return switch (c) {
            case PRINCIPALE -> couleurPrincipale;
            case LATERALE -> couleurLaterale;
        };
    }

    /** Un logo valide, ou {@code null} s'il est absent ou vide ; tout autre contenu est refusé. */
    public static String logoDe(Object v) {
        if (v == null || v instanceof String s && s.isEmpty()) {
            return null;
        }
        Matcher m = v instanceof String s ? LOGO.matcher(s) : null;
        if (m == null || !m.matches()) {
            throw new OptionInvalideException("« logo » : une image PNG, JPEG ou WebP (le SVG est refusé : il peut contenir du script)");
        }
        if ((long) m.group(2).length() * OCTETS_PAR_QUATRE_CARACTERES / CARACTERES_PAR_BLOC > LOGO_MAX_OCTETS) {
            throw new OptionInvalideException("« logo » : " + LOGO_MAX_OCTETS / OCTETS_PAR_KO + " Ko au plus");
        }
        return (String) v;
    }

    /** La charte d'un corps reçu, validée champ par champ, puis contraste par contraste. */
    public static Charte de(Map<String, Object> brut) {
        String nom = brut.get("nomAffiche") instanceof String s ? s.strip() : "";
        if (nom.isEmpty() || nom.length() > NOM_MAX) {
            throw new OptionInvalideException("« nomAffiche » : de 1 à " + NOM_MAX + " caractères");
        }
        Charte c = new Charte(nom, Contrastes.couleurDe(brut.get(CouleurCharte.PRINCIPALE.code()), CouleurCharte.PRINCIPALE.code()),
                Contrastes.couleurDe(brut.get(CouleurCharte.LATERALE.code()), CouleurCharte.LATERALE.code()), logoDe(brut.get("logo")));
        c.exigerContrastes();
        return c;
    }

    private void exigerContrastes() {
        List<String> manques = Arrays.stream(ExigenceContraste.values()).filter(e -> e.mesure(couleur(e.couleur())) < e.minimum())
                .map(this::manque).toList();
        if (!manques.isEmpty()) {
            throw new OptionInvalideException("contraste insuffisant : " + String.join(" ; ", manques));
        }
    }

    private String manque(ExigenceContraste e) {
        String couleur = couleur(e.couleur());
        return e.couleur().code() + " " + couleur + " — " + e.role() + " " + Contrastes.nombre(e.mesure(couleur))
                + ":1 (il faut " + Contrastes.nombre(e.minimum()) + ":1)";
    }

    /** Les variables CSS que la charte pose ; les nuances se déduisent, elles ne se saisissent pas. */
    public Map<String, String> variables() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("--primary", couleurPrincipale);
        v.put("--primary-hover", Contrastes.nuance(couleurPrincipale, FACTEUR_SURVOL));
        v.put("--primary-soft", Contrastes.nuance(couleurPrincipale, FACTEUR_DOUX));
        v.put("--ring", "0 0 0 3px rgba(" + Contrastes.canauxDecimaux(couleurPrincipale) + ", 0.28)");
        v.put("--sidebar", couleurLaterale);
        return v;
    }

}

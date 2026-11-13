package ca.vina.nexu.organisme.domaine;

/**
 * Ce que chaque couleur de la charte doit tenir, dans l'interface telle qu'elle est : la couleur principale
 * porte du texte blanc (boutons, entrée de menu active) et sert de texte de lien sur les deux fonds clairs ;
 * la barre latérale porte trois gris de texte. Jumeau de {@code EXIGENCES} du Node, dans le même ordre — les
 * refus énumèrent les manques dans cet ordre.
 */
public enum ExigenceContraste {
    BOUTONS(CouleurCharte.PRINCIPALE, "#ffffff", "texte blanc sur les boutons", 0),
    LIENS(CouleurCharte.PRINCIPALE, "#f4f6fb", "liens sur le fond de page", 0),
    TITRE_LATERAL(CouleurCharte.LATERALE, "#ffffff", "titre de la barre latérale", 0),
    MENU(CouleurCharte.LATERALE, "#cbd5e1", "entrées du menu", 0),
    PIED_LATERAL(CouleurCharte.LATERALE, "#94a3b8", "pied de la barre latérale", 0),
    // Le bloc de l'organisme pose un voile blanc à 12 % sur la barre : le fond réel est plus clair que la
    // couleur choisie (trouvé par axe-core sur une charte bleu marine, 3,99:1).
    BLOC_ORGANISME(CouleurCharte.LATERALE, "#94a3b8", "bloc de l'organisme (voile clair)", 0.12);

    /** WCAG 2.2, 1.4.3 : texte courant. */
    public static final double MINIMUM = 4.5;

    private final CouleurCharte couleur;
    private final String autre;
    private final String role;
    private final double voile;

    ExigenceContraste(CouleurCharte couleur, String autre, String role, double voile) {
        this.couleur = couleur;
        this.autre = autre;
        this.role = role;
        this.voile = voile;
    }

    public CouleurCharte couleur() {
        return couleur;
    }

    public String autre() {
        return autre;
    }

    public String role() {
        return role;
    }

    public double minimum() {
        return MINIMUM;
    }

    public double voile() {
        return voile;
    }

    /** Le contraste mesuré pour cette exigence, sur le fond réellement vu (sous le voile, s'il y en a un). */
    public double mesure(String couleurChoisie) {
        String fond = voile > 0 ? Contrastes.sousVoile(couleurChoisie, voile) : couleurChoisie;
        return Contrastes.contraste(fond, autre);
    }
}

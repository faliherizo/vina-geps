package ca.vina.nexu.organisme.domaine;

/**
 * Le réglage du déploiement pour la restriction de pays : le nom de l'en-tête où la passerelle pose le pays
 * d'origine d'une connexion ({@code PAYS_ENTETE}). Sans lui, le serveur ne sait pas d'où l'on se connecte :
 * activer une restriction bloquerait alors tout le monde, et c'est à la configuration qu'on le refuse.
 *
 * @param nomEntete {@code null} ou vide si la passerelle ne transmet pas le pays
 */
public record ReglagesPays(String nomEntete) {

    public boolean estConfigure() {
        return nomEntete != null && !nomEntete.isEmpty();
    }
}

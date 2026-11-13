package ca.vina.nexu.partage.identifiants.domaine;

/** Port : les identifiants préfixés du magasin ({@code store.nextId}) et les UUID — injectés, donc rejouables en essai. */
public interface Sequence {
    String suivant(String prefixe);

    String uuid();

    /** Un jeton public de signature : 48 caractères hexadécimaux, comme le Node. */
    String jetonSignature();

    /** {@code randomBytes(n).toString("base64url")} : un secret d'usage unique (jeton d'accès, mot de passe provisoire). */
    String jetonBase64Url(int octets);
}

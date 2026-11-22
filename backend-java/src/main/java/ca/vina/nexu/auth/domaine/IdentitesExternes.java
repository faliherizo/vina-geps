package ca.vina.nexu.auth.domaine;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * <strong>Les identités externes</strong> (ADR 0093) : ce qu'un fournisseur d'identité a prouvé — le couple
 * {@code (émetteur, sujet)} d'un jeton OIDC, ou l'émetteur et le {@code NameID} d'une assertion SAML — et le
 * compte de l'application auquel ce couple est <em>relié</em>.
 *
 * <p>Le courriel n'est pas une identité : un fournisseur le laisse souvent modifier, et deux fournisseurs peuvent
 * déclarer le même. Il ne sert que d'<em>indice</em> pour proposer un compte ; la liaison exige une preuve que la
 * personne possède ce compte ({@link Preuve}). Une fois reliée, l'identité retrouve le compte à chaque connexion
 * par {@code (émetteur, sujet)} seulement.
 */
public final class IdentitesExternes {

    /** La longueur que la base accepte : un émetteur est une URL, un sujet un identifiant opaque (RFC 7519 : ≤ 255 en pratique). */
    private static final int EMETTEUR_MAX = 512;
    private static final int SUJET_MAX = 255;
    /** Le nombre de caractères du courriel laissés lisibles dans l'indice : assez pour se reconnaître, trop peu pour l'apprendre. */
    private static final int INDICE_VISIBLE = 1;

    private IdentitesExternes() {
    }

    /** L'émetteur ({@code iss}) d'un jeton, ou l'entité d'un fournisseur SAML. */
    public record Emetteur(String valeur) {
        public Emetteur {
            valeur = exiger(valeur, EMETTEUR_MAX, "émetteur");
        }
    }

    /** Le sujet ({@code sub}) d'un jeton, ou le {@code NameID} d'une assertion : stable pour ce fournisseur. */
    public record Sujet(String valeur) {
        public Sujet {
            valeur = exiger(valeur, SUJET_MAX, "sujet");
        }
    }

    /** La clé d'une identité externe : l'émetteur et le sujet, ensemble. */
    public record Cle(Emetteur emetteur, Sujet sujet) {
        public static Cle de(Object emetteur, Object sujet) {
            return new Cle(new Emetteur(texte(emetteur)), new Sujet(texte(sujet)));
        }
    }

    /**
     * D'où vient l'identité : un annuaire d'organisme (avec son organisme) ou le fournisseur d'instance
     * (Entra ID ou SAML réglé par l'environnement), qui n'appartient à aucun organisme.
     */
    public record Provenance(Optional<String> annuaireId, Optional<String> tenantId) {
        public static Provenance instance() {
            return new Provenance(Optional.empty(), Optional.empty());
        }

        public static Provenance annuaire(String annuaireId, String tenantId) {
            return new Provenance(Optional.of(annuaireId), Optional.of(tenantId));
        }
    }

    /** Ce que le fournisseur a prouvé, signature et nonce vérifiés : la clé, le courriel (un indice), la provenance. */
    public record IdentiteVerifiee(Cle cle, String courriel, Provenance provenance) {
    }

    /** Comment la liaison a été prouvée : la colonne {@code preuve} de {@code auth_identites_externes}. */
    public enum Preuve {
        /** Une invitation en cours, adressée au courriel vérifié, a été acceptée par cette identité. */
        INVITATION("invitation"),
        /** La personne a saisi le mot de passe du compte existant. */
        MOT_DE_PASSE("mot-de-passe"),
        /** Un administrateur de l'organisme a approuvé la demande. */
        APPROBATION("approbation"),
        /** L'annuaire autorise le provisionnement juste-à-temps : le compte est né de cette identité. */
        PROVISIONNEMENT("provisionnement-jat");

        private final String code;

        Preuve(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Preuve de(String code) {
            return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("preuve de liaison inconnue : « " + code + " »"));
        }
    }

    /** Une identité reliée : la ligne de {@code auth_identites_externes}. */
    public record Identite(Cle cle, String userId, Provenance provenance, Instant lieeLe, String lieePar, Preuve preuve,
                           Optional<Instant> derniereConnexion) {
    }

    /** Pourquoi une liaison ou une connexion par identité externe est refusée ; chaque motif a son message. */
    public enum Motif {
        COMPTE_INACTIF("le compte de l'application est désactivé"),
        ORGANISME_DIFFERENT("le compte n'appartient pas à l'organisme de cet annuaire"),
        COMPTE_INTROUVABLE("le compte relié à cette identité n'existe plus"),
        PREUVE_REFUSEE("la preuve ne permet pas de relier ce compte"),
        LIAISON_EXPIREE("aucune liaison en cours, ou elle a expiré : reconnectez-vous par votre annuaire"),
        TENTATIVES_EPUISEES("trop d'essais : reconnectez-vous par votre annuaire"),
        AUCUN_CANDIDAT("aucun compte de l'organisme ne porte ce courriel : demandez une invitation à votre administration"),
        DEMANDE_INTROUVABLE("demande de liaison introuvable ou déjà traitée"),
        DEJA_LIEE("cette identité est déjà reliée à un compte");

        private final String message;

        Motif(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    /** Un refus nommé : la frontière HTTP le traduit en 403 (ou 404 pour une liaison expirée, une demande inconnue). */
    public static final class LiaisonRefuseeException extends RuntimeException {
        private final Motif motif;

        public LiaisonRefuseeException(Motif motif) {
            super(motif.message());
            this.motif = motif;
        }

        public Motif motif() {
            return motif;
        }
    }

    /** Une revendication absente ou invalide : le fournisseur n'a rien prouvé d'utilisable. */
    public static final class IdentiteInvalideException extends IllegalArgumentException {
        public IdentiteInvalideException(String message) {
            super(message);
        }
    }

    /** Port de lecture : l'identité reliée à une clé. */
    @FunctionalInterface
    public interface Registre {
        Optional<Identite> trouver(Cle cle);
    }

    /** Port d'écriture : relier une identité, noter sa dernière connexion. */
    public interface Ecriture {
        /** Relie la clé au compte ; une clé déjà reliée lève {@link LiaisonRefuseeException} ({@link Motif#DEJA_LIEE}). */
        void lier(Identite identite);

        void noterConnexion(Cle cle, Instant le);
    }

    /** Port : les comptes, tels que la résolution d'identité les lit, les authentifie, les crée et les reclasse. */
    public interface Comptes {
        Optional<Compte> parId(String id);

        /** Un indice seulement : le compte de l'organisme (ou, sans organisme, l'unique compte) qui porte ce courriel. */
        Optional<Compte> parCourriel(Optional<String> tenantId, String courriel);

        /** Le compte dont ces identifiants sont exacts, dans l'organisme s'il est donné ; vide sinon, sans dire pourquoi. */
        Optional<Compte> authentifier(Optional<String> tenantId, String identifiant, String motDePasse);

        /** Un compte sans mot de passe utilisable : il n'entre que par son annuaire (global sans organisme). */
        Compte creer(Optional<String> tenantId, String courriel, String role);

        Compte reclasser(Compte compte, String role);
    }

    /** Port : l'invitation comme preuve — acceptée par l'identité, elle fait naître le compte invité. */
    @FunctionalInterface
    public interface Invitations {
        /** Vide si le jeton est inconnu, n'est plus offert, vise un autre courriel ou un autre organisme. */
        Optional<Compte> accepter(String jeton, String courrielVerifie, Optional<String> tenantId);
    }

    /**
     * L'autorisation d'un compte pour une provenance : actif, et de l'organisme de l'annuaire. Le fournisseur
     * d'instance n'a pas d'organisme et n'en impose donc aucun.
     *
     * @throws LiaisonRefuseeException sinon
     */
    public static void autoriser(Compte compte, Provenance provenance) {
        if (!compte.active()) {
            throw new LiaisonRefuseeException(Motif.COMPTE_INACTIF);
        }
        if (provenance.tenantId().filter(t -> !t.equals(compte.tenantId())).isPresent()) {
            throw new LiaisonRefuseeException(Motif.ORGANISME_DIFFERENT);
        }
    }

    /** {@code jean.tremblay@x.ca} → {@code j***@x.ca} : de quoi se reconnaître sans révéler l'adresse à qui ne l'a pas. */
    public static String masquer(String courriel) {
        int arobase = courriel.lastIndexOf('@');
        if (arobase <= INDICE_VISIBLE) {
            return "***" + (arobase < 0 ? "" : courriel.substring(arobase));
        }
        return courriel.substring(0, INDICE_VISIBLE) + "***" + courriel.substring(arobase).toLowerCase(Locale.ROOT);
    }

    private static String texte(Object brut) {
        return brut instanceof String s ? s : "";
    }

    private static String exiger(String brut, int max, String nom) {
        String v = brut == null ? "" : brut.trim();
        if (v.isEmpty() || v.length() > max) {
            throw new IdentiteInvalideException("le fournisseur n'a pas transmis de " + nom + " utilisable (" + max + " caractères au plus)");
        }
        return v;
    }
}

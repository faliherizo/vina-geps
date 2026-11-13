package ca.vina.nexu.auth.application;

import ca.vina.nexu.auth.domaine.Invitations;
import ca.vina.nexu.partage.temps.Iso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <strong>Poser, révoquer et accepter une invitation</strong> — jumeau de
 * {@code backend/src/auth/serviceInvitations.ts}. Le lien n'est rendu qu'une fois, à la pose ; il
 * n'existe ensuite nulle part en clair.
 */
public class ServiceInvitations {

    private static final int LONGUEUR_JETON = 32;
    private static final String CHEMIN_LIEN = "/admin/invitation/";
    private static final SecureRandom ALEA = new SecureRandom();

    /** Le résultat d'une pose : l'invitation listée, et le lien à usage unique. */
    public record Pose(Map<String, Object> invitation, String lien, String courriel) {
    }

    private final Invitations.Depot depot;
    private final Invitations.Comptes comptes;
    private final Clock horloge;

    public ServiceInvitations(Invitations.Depot depot, Invitations.Comptes comptes, Clock horloge) {
        this.depot = depot;
        this.comptes = comptes;
        this.horloge = horloge;
    }

    public List<Map<String, Object>> lister(String tenantId) {
        Instant maintenant = horloge.instant();
        return depot.lister(tenantId).stream().map(i -> vue(i, Invitations.etatDe(i, maintenant))).toList();
    }

    public Pose inviter(String tenantId, String acteurId, String base, Map<String, Object> corps, List<String> rolesPermis) {
        Invitations.Saisie v = Invitations.valider(corps, rolesPermis);
        if (depot.compteExiste(tenantId, v.username())) {
            throw new Invitations.InvitationInvalideException("« " + v.username()
                    + " » a déjà un compte dans cet établissement : il se connecte, il ne s'invite pas");
        }
        Instant maintenant = horloge.instant();
        String le = Iso.de(maintenant);
        depot.enCours(tenantId, v.username(), le).ifPresent(e -> {
            throw new Invitations.InvitationInvalideException("une invitation est déjà en cours pour « " + v.username()
                    + " » jusqu'au " + e.expireLe().substring(0, 10) + " — la révoquer d'abord pour en poser une autre");
        });
        String jeton = jeton();
        Invitations.Invitation nouvelle = new Invitations.Invitation(UUID.randomUUID().toString(), tenantId, v.courriel(),
                v.username(), v.role(), v.employeeId(), Iso.de(Invitations.expiration(maintenant, v.jours())), null, null, le,
                acteurId, null);
        depot.poser(nouvelle, empreinte(jeton));
        String lien = base.replaceAll("/+$", "") + CHEMIN_LIEN + jeton;
        return new Pose(vue(nouvelle, Invitations.Etat.OFFERTE), lien, v.courriel());
    }

    public Map<String, Object> revoquer(String tenantId, String id) {
        Invitations.Invitation avant = depot.lire(tenantId, id)
                .orElseThrow(() -> new Invitations.InvitationInvalideException("invitation introuvable"));
        if (avant.accepteeLe() != null) {
            throw new Invitations.InvitationInvalideException("cette invitation a déjà été acceptée : la révoquer ne fermerait pas le compte."
                    + " Archiver le compte depuis la gestion des utilisateurs.");
        }
        String le = Iso.de(horloge.instant());
        depot.revoquer(id, le);
        return vue(new Invitations.Invitation(avant.id(), avant.tenantId(), avant.courriel(), avant.username(), avant.role(),
                avant.employeeId(), avant.expireLe(), avant.accepteeLe(), le, avant.creeLe(), avant.creePar(), avant.compteId()),
                Invitations.Etat.REVOQUEE);
    }

    public Invitations.Publique lireParJeton(String jeton) {
        return Invitations.publique(parJeton(jeton), horloge.instant());
    }

    /** La personne choisit son mot de passe, et le compte naît — double authentification exigée à la première connexion. */
    public Map<String, Object> accepter(String jeton, Map<String, Object> corps) {
        Invitations.Invitation i = parJeton(jeton);
        Invitations.publique(i, horloge.instant());
        String saisi = corps.get("username") == null ? i.username() : String.valueOf(corps.get("username")).trim();
        String username = saisi.isEmpty() ? i.username() : saisi;
        String password = corps.get("password") == null ? "" : String.valueOf(corps.get("password"));
        String compteId;
        try {
            compteId = comptes.creer(i, username, password);
        } catch (IllegalArgumentException e) {
            throw new Invitations.InvitationInvalideException(e.getMessage() == null ? "activation refusée" : e.getMessage());
        }
        depot.accepter(i.id(), Iso.de(horloge.instant()), compteId, username);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("compteId", compteId);
        m.put("username", username);
        return m;
    }

    private Invitations.Invitation parJeton(String jeton) {
        return depot.parEmpreinte(empreinte(jeton == null ? "" : jeton))
                .orElseThrow(() -> new Invitations.InvitationInvalideException("ce lien d'invitation n'existe pas"));
    }

    private static Map<String, Object> vue(Invitations.Invitation i, Invitations.Etat etat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.id());
        m.put("tenantId", i.tenantId());
        m.put("courriel", i.courriel());
        m.put("username", i.username());
        m.put("role", i.role());
        m.put("employeeId", i.employeeId());
        m.put("expireLe", i.expireLe());
        m.put("accepteeLe", i.accepteeLe());
        m.put("revoqueeLe", i.revoqueeLe());
        m.put("creeLe", i.creeLe());
        m.put("creePar", i.creePar());
        m.put("compteId", i.compteId());
        m.put("etat", etat.code());
        return m;
    }

    private static String jeton() {
        byte[] octets = new byte[LONGUEUR_JETON];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    static String empreinte(String jeton) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

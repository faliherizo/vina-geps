package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.auth.domaine.Invitations;
import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.temps.Iso;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * L'invitation comme preuve de liaison (ADR 0093) : la personne colle le lien reçu par courriel (ou son seul jeton).
 * L'invitation doit être offerte, adressée au courriel que l'annuaire a vérifié, et de l'organisme de l'annuaire ;
 * elle est alors acceptée, et le compte invité naît — sans mot de passe utilisable, puisqu'il entre par l'annuaire.
 */
public class AdaptateurInvitationsLiaison implements IdentitesExternes.Invitations {

    private static final int OCTETS_MOT_DE_PASSE = 24;

    private final Invitations.Depot invitations;
    private final DepotComptes comptes;
    private final Clock horloge;
    private final RandomGenerator alea;

    public AdaptateurInvitationsLiaison(Invitations.Depot invitations, DepotComptes comptes, Clock horloge, RandomGenerator alea) {
        this.invitations = invitations;
        this.comptes = comptes;
        this.horloge = horloge;
        this.alea = alea;
    }

    /** Le jeton seul, que la personne ait collé le lien entier ou non. */
    static String jetonDe(String saisi) {
        return saisi.trim().replaceAll("^.*/invitation/|[/?#].*$", "");
    }

    static String empreinte(String jeton) {
        return HexFormat.of().formatHex(Primitives.sha256(jeton.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Optional<Compte> accepter(String jeton, String courrielVerifie, Optional<String> tenantId) {
        return invitations.parEmpreinte(empreinte(jetonDe(jeton)))
                .filter(i -> Invitations.etatDe(i, horloge.instant()) == Invitations.Etat.OFFERTE)
                .filter(i -> i.courriel().equalsIgnoreCase(courrielVerifie))
                .filter(i -> tenantId.map(t -> t.equals(i.tenantId())).orElse(true))
                .map(this::creer);
    }

    private Compte creer(Invitations.Invitation i) {
        byte[] b = new byte[OCTETS_MOT_DE_PASSE];
        alea.nextBytes(b);
        Compte c = comptes.creer(new DepotComptes.Creation(i.username(), Primitives.base64Url(b), i.role(), i.employeeId(), i.tenantId(),
                false, false, i.courriel(), false));
        invitations.accepter(i.id(), Iso.de(horloge.instant()), c.id(), i.username());
        return c;
    }
}

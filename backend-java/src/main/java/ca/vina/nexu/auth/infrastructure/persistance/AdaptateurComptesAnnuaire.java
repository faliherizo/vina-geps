package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Annuaires;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.partage.crypto.Primitives;

import java.util.Optional;
import java.util.random.RandomGenerator;

/** Les comptes d'un organisme vus d'une connexion par annuaire (P-TN-2), sur la table {@code users}. */
public class AdaptateurComptesAnnuaire implements Annuaires.Comptes {

    private static final int OCTETS_MOT_DE_PASSE = 24;

    private final DepotComptes comptes;
    private final RandomGenerator alea;

    public AdaptateurComptesAnnuaire(DepotComptes comptes, RandomGenerator alea) {
        this.comptes = comptes;
        this.alea = alea;
    }

    /** Dans l'organisme seulement : le même courriel ailleurs est un autre compte. */
    @Override
    public Optional<Compte> duCourriel(String tenantId, String courriel) {
        return comptes.lister().stream()
                .filter(u -> tenantId.equals(u.tenantId()) && (courriel.equalsIgnoreCase(u.email()) || courriel.equalsIgnoreCase(u.nomDeConnexion())))
                .findFirst();
    }

    @Override
    public Compte creer(String tenantId, String courriel, String role) {
        byte[] b = new byte[OCTETS_MOT_DE_PASSE];
        alea.nextBytes(b);
        // Mot de passe aléatoire jamais communiqué : ce compte n'entre que par son annuaire.
        return comptes.creer(new DepotComptes.Creation(courriel, Primitives.base64Url(b), role, null, tenantId, false, false, courriel, false));
    }

    @Override
    public Compte reclasser(Compte compte, String role) {
        return comptes.modifier(compte.id(), new DepotComptes.Modification(role, null, null, null, null, null, null));
    }
}

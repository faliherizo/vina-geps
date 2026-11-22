package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.partage.crypto.Primitives;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

/**
 * Les comptes vus de la résolution d'identité (ADR 0093), sur la table {@code users} : lus, authentifiés par mot de
 * passe pour une preuve de possession, créés sans mot de passe utilisable, reclassés.
 */
public class AdaptateurComptesAnnuaire implements IdentitesExternes.Comptes {

    private static final int OCTETS_MOT_DE_PASSE = 24;

    private final DepotComptes comptes;
    private final RandomGenerator alea;

    public AdaptateurComptesAnnuaire(DepotComptes comptes, RandomGenerator alea) {
        this.comptes = comptes;
        this.alea = alea;
    }

    @Override
    public Optional<Compte> parId(String id) {
        return comptes.parId(id);
    }

    private Stream<Compte> portes(Optional<String> tenantId, String identifiant) {
        return comptes.lister().stream()
                .filter(u -> tenantId.map(t -> t.equals(u.tenantId())).orElse(true))
                .filter(u -> identifiant.equalsIgnoreCase(u.email()) || identifiant.equalsIgnoreCase(u.nomDeConnexion()));
    }

    /**
     * Dans l'organisme seulement : le même courriel ailleurs est un autre compte. Sans organisme (fournisseur
     * d'instance), un courriel porté par plusieurs comptes ne désigne personne.
     */
    @Override
    public Optional<Compte> parCourriel(Optional<String> tenantId, String courriel) {
        List<Compte> trouves = portes(tenantId, courriel).toList();
        return trouves.size() == 1 ? Optional.of(trouves.get(0)) : Optional.empty();
    }

    @Override
    public Optional<Compte> authentifier(Optional<String> tenantId, String identifiant, String motDePasse) {
        List<Compte> valides = portes(tenantId, identifiant).filter(u -> u.active() && comptes.verifierMotDePasse(u, motDePasse)).toList();
        return valides.size() == 1 ? Optional.of(valides.get(0)) : Optional.empty();
    }

    @Override
    public Compte creer(Optional<String> tenantId, String courriel, String role) {
        byte[] b = new byte[OCTETS_MOT_DE_PASSE];
        alea.nextBytes(b);
        // Mot de passe aléatoire jamais communiqué : ce compte n'entre que par son annuaire.
        return comptes.creer(new DepotComptes.Creation(courriel, Primitives.base64Url(b), role, null, tenantId.orElse(null), false, false, courriel, false));
    }

    @Override
    public Compte reclasser(Compte compte, String role) {
        return comptes.modifier(compte.id(), new DepotComptes.Modification(role, null, null, null, null, null, null));
    }
}

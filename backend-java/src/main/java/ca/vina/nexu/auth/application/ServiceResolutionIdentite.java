package ca.vina.nexu.auth.application;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.DemandesLiaison;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.auth.domaine.IdentitesExternes.IdentiteVerifiee;
import ca.vina.nexu.auth.domaine.IdentitesExternes.LiaisonRefuseeException;
import ca.vina.nexu.auth.domaine.IdentitesExternes.Motif;
import ca.vina.nexu.auth.domaine.IdentitesExternes.Preuve;
import ca.vina.nexu.auth.domaine.Oidc;

import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * <strong>Le service de résolution d'identité</strong> (ADR 0093). Après la vérification du jeton (signature,
 * émetteur, audience, échéance, nonce), il reçoit le couple {@code (émetteur, sujet)} et répond :
 * <ul>
 *   <li><strong>trouvé</strong> dans {@code auth_identites_externes} : le compte relié, autorisé (actif, du bon
 *       organisme), son rôle suivi selon l'annuaire ;</li>
 *   <li><strong>introuvable</strong> : jamais de second compte à l'aveugle. Le compte naît de l'identité seulement si
 *       l'annuaire autorise le provisionnement juste-à-temps et qu'aucun compte de l'organisme ne porte déjà ce
 *       courriel ; sinon, une <em>liaison en cours</em> attend une preuve de possession du compte existant —
 *       invitation, mot de passe, ou approbation d'un administrateur.</li>
 * </ul>
 * La liaison en cours vit dix minutes, en mémoire du processus comme les demandes OIDC, et n'admet que
 * {@value #ESSAIS_MAX} essais de preuve.
 */
public class ServiceResolutionIdentite {

    /** Au-delà, la personne repasse par son annuaire : une liaison en cours n'est pas un oracle de mots de passe. */
    public static final int ESSAIS_MAX = 5;
    private static final int OCTETS_JETON = 32;
    private static final int OCTETS_DEMANDE = 12;
    private static final String PREFIXE_DEMANDE = "liaison-";
    /** L'acteur d'un provisionnement juste-à-temps : aucune personne ne l'a décidé, la configuration de l'annuaire si. */
    public static final String SYSTEME = "systeme";

    /** Ce que la provenance accorde : le rôle à porter, s'il suit l'annuaire à chaque connexion, et le provisionnement JAT. */
    public record Admission(Optional<String> role, boolean reclasser, boolean provisionnementJat) {
    }

    /** L'issue d'une résolution. */
    public sealed interface Issue permits Reconnue, LiaisonRequise {
    }

    /** Le compte de l'identité ; {@code cree} s'il vient d'être provisionné. */
    public record Reconnue(Compte compte, boolean cree) implements Issue {
    }

    /** Aucune identité reliée : la personne doit prouver qu'elle possède un compte. Le jeton désigne la liaison en cours. */
    public record LiaisonRequise(String jeton, String courriel) implements Issue {
    }

    /** Ce que l'écran « Relier mon compte » peut montrer : un indice masqué, et ce qui reste possible. */
    public record EtatLiaison(String indice, boolean candidat, boolean demandeEnAttente, int essaisRestants) {
    }

    /** Une preuve acceptée : le compte relié, et l'identité qui l'a été (pour le journal). */
    public record Liaison(Compte compte, IdentiteVerifiee identite, Preuve preuve) {
    }

    public record Ports(IdentitesExternes.Registre registre, IdentitesExternes.Ecriture ecriture, IdentitesExternes.Comptes comptes,
                        IdentitesExternes.Invitations invitations, DemandesLiaison.Registre demandes, DemandesLiaison.Ecriture ecritureDemandes) {
    }

    private record EnCours(IdentiteVerifiee identite, Admission admission, long creeLe, int essais) {
        EnCours essai() {
            return new EnCours(identite, admission, creeLe, essais + 1);
        }
    }

    private final Ports ports;
    private final Clock horloge;
    private final RandomGenerator alea;
    private final Map<String, EnCours> enCours = new ConcurrentHashMap<>();

    public ServiceResolutionIdentite(Ports ports, Clock horloge, RandomGenerator alea) {
        this.ports = ports;
        this.horloge = horloge;
        this.alea = alea;
    }

    // ── Résolution ──────────────────────────────────────────────────────────

    public Issue resoudre(IdentiteVerifiee v, Admission a) {
        Optional<IdentitesExternes.Identite> liee = ports.registre().trouver(v.cle());
        if (liee.isPresent()) {
            Compte c = ports.comptes().parId(liee.get().userId()).orElseThrow(() -> new LiaisonRefuseeException(Motif.COMPTE_INTROUVABLE));
            IdentitesExternes.autoriser(c, v.provenance());
            ports.ecriture().noterConnexion(v.cle(), horloge.instant());
            return new Reconnue(appliquer(c, a), false);
        }
        if (provisionnable(v, a)) {
            Compte c = ports.comptes().creer(v.provenance().tenantId(), v.courriel(), a.role().orElseThrow());
            lier(v, c, Preuve.PROVISIONNEMENT, SYSTEME);
            return new Reconnue(c, true);
        }
        purger();
        String jeton = aleatoire(OCTETS_JETON);
        enCours.put(jeton, new EnCours(v, a, horloge.millis(), 0));
        return new LiaisonRequise(jeton, v.courriel());
    }

    /** Le juste-à-temps n'écrase jamais un compte existant : un courriel déjà porté exige une preuve, comme sans JAT. */
    private boolean provisionnable(IdentiteVerifiee v, Admission a) {
        return a.provisionnementJat() && a.role().isPresent() && ports.comptes().parCourriel(v.provenance().tenantId(), v.courriel()).isEmpty();
    }

    private Compte appliquer(Compte c, Admission a) {
        Optional<String> nouveau = a.role().filter(r -> a.reclasser() && !r.equals(c.role()));
        return nouveau.isPresent() ? ports.comptes().reclasser(c, nouveau.get()) : c;
    }

    private void lier(IdentiteVerifiee v, Compte c, Preuve preuve, String par) {
        if (ports.registre().trouver(v.cle()).isPresent()) {
            throw new LiaisonRefuseeException(Motif.DEJA_LIEE);
        }
        ports.ecriture().lier(new IdentitesExternes.Identite(v.cle(), c.id(), v.provenance(), horloge.instant(), par, preuve,
                Optional.of(horloge.instant())));
    }

    // ── Liaison en cours ────────────────────────────────────────────────────

    private void purger() {
        long maintenant = horloge.millis();
        enCours.values().removeIf(e -> maintenant - e.creeLe() > Oidc.DUREE_DEMANDE_MS);
    }

    private EnCours lire(String jeton) {
        purger();
        return Optional.ofNullable(jeton).map(enCours::get).orElseThrow(() -> new LiaisonRefuseeException(Motif.LIAISON_EXPIREE));
    }

    /** Compte l'essai avant de le juger : un échec ne se rejoue pas indéfiniment. */
    private EnCours essayer(String jeton) {
        EnCours e = lire(jeton);
        if (e.essais() >= ESSAIS_MAX) {
            enCours.remove(jeton);
            throw new LiaisonRefuseeException(Motif.TENTATIVES_EPUISEES);
        }
        EnCours compte = e.essai();
        enCours.put(jeton, compte);
        return compte;
    }

    public EtatLiaison etat(String jeton) {
        EnCours e = lire(jeton);
        IdentiteVerifiee v = e.identite();
        boolean candidat = ports.comptes().parCourriel(v.provenance().tenantId(), v.courriel()).isPresent();
        return new EtatLiaison(IdentitesExternes.masquer(v.courriel()), candidat, ports.demandes().enAttentePour(v.cle()).isPresent(),
                ESSAIS_MAX - e.essais());
    }

    /** Preuve (b) : le mot de passe du compte existant. Sans identifiant saisi, le courriel vérifié en tient lieu. */
    public Liaison prouverParMotDePasse(String jeton, String identifiant, String motDePasse) {
        EnCours e = essayer(jeton);
        IdentiteVerifiee v = e.identite();
        String qui = identifiant == null || identifiant.isBlank() ? v.courriel() : identifiant.trim();
        Compte c = ports.comptes().authentifier(v.provenance().tenantId(), qui, motDePasse)
                .orElseThrow(() -> new LiaisonRefuseeException(Motif.PREUVE_REFUSEE));
        return relier(jeton, e, c, Preuve.MOT_DE_PASSE);
    }

    /** Preuve (a) : une invitation en cours, adressée au courriel que l'annuaire a vérifié ; le compte invité naît relié. */
    public Liaison prouverParInvitation(String jeton, String invitation) {
        EnCours e = essayer(jeton);
        IdentiteVerifiee v = e.identite();
        Compte c = ports.invitations().accepter(invitation == null ? "" : invitation, v.courriel(), v.provenance().tenantId())
                .orElseThrow(() -> new LiaisonRefuseeException(Motif.PREUVE_REFUSEE));
        return relier(jeton, e, c, Preuve.INVITATION);
    }

    private Liaison relier(String jeton, EnCours e, Compte c, Preuve preuve) {
        IdentitesExternes.autoriser(c, e.identite().provenance());
        lier(e.identite(), c, preuve, c.id());
        enCours.remove(jeton);
        return new Liaison(appliquer(c, e.admission()), e.identite(), preuve);
    }

    /**
     * Preuve (c) : la demande à l'administration, pour le compte que le courriel désigne dans l'organisme. Une
     * demande déjà en attente pour cette identité est rendue telle quelle.
     */
    public DemandesLiaison.Demande demanderApprobation(String jeton) {
        IdentiteVerifiee v = lire(jeton).identite();
        // Un compte global n'a pas d'administration d'organisme pour l'approuver.
        Compte candidat = ports.comptes().parCourriel(v.provenance().tenantId(), v.courriel()).filter(c -> c.tenantId() != null)
                .orElseThrow(() -> new LiaisonRefuseeException(Motif.AUCUN_CANDIDAT));
        IdentitesExternes.autoriser(candidat, v.provenance());
        return ports.demandes().enAttentePour(v.cle()).orElseGet(() -> {
            DemandesLiaison.Demande d = new DemandesLiaison.Demande(PREFIXE_DEMANDE + aleatoire(OCTETS_DEMANDE), v.cle(), v.courriel(),
                    candidat.tenantId(), v.provenance().annuaireId(), candidat.id(), DemandesLiaison.Statut.EN_ATTENTE, horloge.instant(),
                    Optional.empty(), Optional.empty());
            ports.ecritureDemandes().poser(d);
            return d;
        });
    }

    private String aleatoire(int octets) {
        byte[] b = new byte[octets];
        alea.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}

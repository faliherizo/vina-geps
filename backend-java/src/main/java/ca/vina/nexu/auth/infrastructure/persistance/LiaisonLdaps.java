package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Annuaires;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPURL;
import com.unboundid.ldap.sdk.RDN;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.HostNameSSLSocketVerifier;

import javax.net.ssl.SSLSocketFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * <strong>La liaison LDAPS</strong> (ADR 0083 ; FX-AO-20260810, critère 82) : le compte de service cherche la personne
 * sous la base de l'URL, puis une seconde liaison, au DN trouvé, vérifie son mot de passe.
 *
 * <p>Garde-fous : TLS seulement, nom d'hôte du certificat vérifié contre l'URL ; un mot de passe vide est refusé
 * <em>avant</em> tout appel (en LDAP, une liaison sans mot de passe réussit en anonyme) ; le filtre est bâti par
 * l'API, jamais par concaténation (un identifiant ne peut pas élargir la recherche) ; une recherche qui trouve
 * zéro ou plusieurs personnes ne connecte personne.
 */
public final class LiaisonLdaps implements Annuaires.Liaison {

    /** Les attributs où chercher l'identifiant saisi : courriel, UPN et nom de connexion d'Active Directory, uid d'OpenLDAP. */
    enum AttributIdentifiant {
        COURRIEL("mail"), UPN("userPrincipalName"), NOM_DE_CONNEXION("sAMAccountName"), UID("uid");

        private final String nom;

        AttributIdentifiant(String nom) {
            this.nom = nom;
        }

        String nom() {
            return nom;
        }
    }

    private static final String COURRIEL = "mail";
    private static final String MEMBRE_DE = "memberOf";
    private static final int PLAFOND_RESULTATS = 2;
    private static final int IDENTIFIANT_MAX = 256;

    private final SSLSocketFactory tls;
    private final Duration delai;

    /** @param tls la fabrique TLS : en production, celle de la JVM, dont le magasin de confiance porte l'autorité de l'établissement */
    public LiaisonLdaps(SSLSocketFactory tls, Duration delai) {
        this.tls = tls;
        this.delai = delai;
    }

    @Override
    public Optional<Annuaires.Personne> authentifier(Annuaires.Annuaire annuaire, String secretService, String identifiant, String motDePasse) {
        if (identifiant == null || identifiant.isBlank() || identifiant.length() > IDENTIFIANT_MAX || motDePasse == null || motDePasse.isEmpty()) {
            return Optional.empty();
        }
        LDAPURL url = url(annuaire.emetteur());
        try (LDAPConnection service = connecter(url)) {
            service.bind(annuaire.clientId(), secretService);
            Optional<SearchResultEntry> personne = chercher(service, url, identifiant.trim());
            return personne.filter(p -> verifier(url, p.getDN(), motDePasse)).map(LiaisonLdaps::personne);
        } catch (LDAPException e) {
            throw new IllegalStateException(e.getResultCode() + " (" + url.getHost() + ")", e);
        }
    }

    private static LDAPURL url(String brute) {
        try {
            return new LDAPURL(brute);
        } catch (LDAPException e) {
            throw new IllegalStateException("URL LDAPS illisible", e);
        }
    }

    private LDAPConnection connecter(LDAPURL url) throws LDAPException {
        LDAPConnectionOptions options = new LDAPConnectionOptions();
        options.setConnectTimeoutMillis((int) delai.toMillis());
        options.setResponseTimeoutMillis(delai.toMillis());
        options.setSSLSocketVerifier(new HostNameSSLSocketVerifier(true));
        // Sans port écrit, l'URL ldaps:// vaut 636.
        return new LDAPConnection(tls, options, url.getHost(), url.getPort());
    }

    private static Optional<SearchResultEntry> chercher(LDAPConnection service, LDAPURL url, String identifiant) throws LDAPException {
        List<Filter> ou = Arrays.stream(AttributIdentifiant.values()).map(a -> Filter.createEqualityFilter(a.nom(), identifiant)).toList();
        SearchRequest demande = new SearchRequest(url.getBaseDN().toString(), SearchScope.SUB, Filter.createORFilter(ou), COURRIEL, MEMBRE_DE);
        // Deux suffisent pour savoir que l'identifiant est ambigu : inutile de lire tout l'annuaire.
        demande.setSizeLimit(PLAFOND_RESULTATS);
        SearchResult r;
        try {
            r = service.search(demande);
        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED) {
                return Optional.empty();
            }
            throw e;
        }
        return r.getEntryCount() == 1 && r.getSearchEntries().getFirst().hasAttribute(COURRIEL)
                ? Optional.of(r.getSearchEntries().getFirst()) : Optional.empty();
    }

    /** Une liaison au nom de la personne, sur une connexion à part : le compte de service garde la sienne. */
    private boolean verifier(LDAPURL url, String dn, String motDePasse) {
        try (LDAPConnection personne = connecter(url)) {
            personne.bind(dn, motDePasse);
            return true;
        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
                return false;
            }
            throw new IllegalStateException(e.getResultCode() + " (" + url.getHost() + ")", e);
        }
    }

    /** Chaque groupe sous ses deux noms : le DN complet, et son CN — l'administrateur déclare l'un ou l'autre. */
    private static Annuaires.Personne personne(SearchResultEntry e) {
        List<String> groupes = new ArrayList<>();
        for (String dn : Optional.ofNullable(e.getAttributeValues(MEMBRE_DE)).orElse(new String[0])) {
            groupes.add(dn);
            cn(dn).ifPresent(groupes::add);
        }
        return new Annuaires.Personne(e.getAttributeValue(COURRIEL).toLowerCase(Locale.ROOT), groupes);
    }

    private static Optional<String> cn(String dn) {
        try {
            RDN premier = new DN(dn).getRDN();
            return premier != null && premier.hasAttribute("cn") ? Optional.of(premier.getAttributeValues()[0]) : Optional.empty();
        } catch (LDAPException e) {
            return Optional.empty();
        }
    }
}

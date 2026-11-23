package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Annuaires;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSimpleBindRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import com.unboundid.util.ssl.TrustStoreTrustManager;
import com.unboundid.util.ssl.cert.ManageCertificates;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La liaison LDAPS contre un <strong>vrai serveur LDAPS</strong> (UnboundID en mémoire, certificat auto-signé de
 * l'essai) : rien n'est simulé du protocole ni du TLS (ADR 0083, critère 82).
 */
@DisplayName("LiaisonLdaps — authentification par liaison LDAPS, contre un serveur LDAPS en mémoire")
class LiaisonLdapsTest {

    private static final String BASE = "dc=us,dc=local";
    private static final String SERVICE = "cn=svc-geps,ou=services," + BASE;
    private static final Duration DELAI = Duration.ofSeconds(5);
    private static final String NIP = "nip-de-l-essai";
    private static InMemoryDirectoryServer serveur;
    private static SSLSocketFactory confiance;

    @BeforeAll
    static void demarrer() throws Exception {
        // Un certificat au nom de « localhost » seulement : la vérification du nom d'hôte est éprouvée, pas contournée.
        File magasin = magasin("localhost");
        SSLUtil cote = new SSLUtil(new KeyStoreKeyManager(magasin, NIP.toCharArray(), "PKCS12", null), new TrustAllTrustManager());
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE);
        // Sans schéma : l'essai porte les attributs d'Active Directory (sAMAccountName, memberOf) sans les déclarer.
        config.setSchema(null);
        // L'annuaire tombe pendant la liaison de « panne » : une panne, pas un mauvais mot de passe.
        config.addInMemoryOperationInterceptor(new InMemoryOperationInterceptor() {
            @Override
            public void processSimpleBindRequest(InMemoryInterceptedSimpleBindRequest r) throws LDAPException {
                if (r.getRequest().getBindDN().startsWith("cn=Panne")) {
                    throw new LDAPException(ResultCode.UNAVAILABLE, "indisponible");
                }
            }
        });
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPSConfig("LDAPS", 0, cote.createSSLServerSocketFactory()));
        serveur = new InMemoryDirectoryServer(config);
        serveur.add("dn: " + BASE, "objectClass: domain", "dc: us");
        serveur.add("dn: ou=services," + BASE, "objectClass: organizationalUnit", "ou: services");
        serveur.add("dn: ou=personnel," + BASE, "objectClass: organizationalUnit", "ou: personnel");
        serveur.add("dn: " + SERVICE, "objectClass: person", "cn: svc-geps", "sn: svc", "userPassword: secret-service");
        serveur.add("dn: cn=Marie Roy,ou=personnel," + BASE, "objectClass: person", "cn: Marie Roy", "sn: Roy", "sAMAccountName: mroy",
                "mail: Marie.Roy@Urgences-Sante.qc.ca", "userPassword: bon-mot",
                "memberOf: CN=GEPS-Chefs,OU=Groupes,DC=us,DC=local", "memberOf: OU=Sans-CN,DC=us,DC=local", "memberOf: pas un DN");
        serveur.add("dn: cn=Panne,ou=personnel," + BASE, "objectClass: person", "cn: Panne", "sn: P", "uid: panne", "mail: panne@urgences-sante.qc.ca",
                "userPassword: x");
        serveur.add("dn: cn=Sans Courriel,ou=personnel," + BASE, "objectClass: person", "cn: Sans Courriel", "sn: C", "uid: sanscourriel", "userPassword: x");
        for (String n : List.of("1", "2")) {
            serveur.add("dn: cn=Double " + n + ",ou=personnel," + BASE, "objectClass: person", "cn: Double " + n, "sn: D", "uid: double",
                    "mail: double" + n + "@urgences-sante.qc.ca", "userPassword: x");
        }
        for (String n : List.of("1", "2", "3")) {
            serveur.add("dn: cn=Triple " + n + ",ou=personnel," + BASE, "objectClass: person", "cn: Triple " + n, "sn: T", "uid: triple",
                    "mail: triple" + n + "@urgences-sante.qc.ca", "userPassword: x");
        }
        serveur.startListening();
        confiance = new SSLUtil(new TrustStoreTrustManager(magasin, NIP.toCharArray(), "PKCS12", true)).createSSLSocketFactory();
    }

    private static File magasin(String hote) throws Exception {
        File f = Files.createTempFile("essai-ldaps", ".p12").toFile();
        Files.delete(f.toPath());
        f.deleteOnExit();
        ResultCode cree = ManageCertificates.main(null, OutputStream.nullOutputStream(), OutputStream.nullOutputStream(), "generate-self-signed-certificate",
                "--keystore", f.getPath(), "--keystore-password", NIP, "--keystore-type", "PKCS12", "--alias", "serveur",
                "--subject-dn", "CN=" + hote, "--subject-alternative-name-dns", hote, "--key-algorithm", "RSA", "--key-size-bits", "2048");
        assertThat(cree).isEqualTo(ResultCode.SUCCESS);
        return f;
    }

    @Test
    @DisplayName("un certificat reconnu, mais au nom d'un autre hôte que celui de l'URL : la connexion est refusée")
    void nomDHote() throws Exception {
        File autre = magasin("autre.hote");
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPSConfig("LDAPS", 0,
                new SSLUtil(new KeyStoreKeyManager(autre, NIP.toCharArray(), "PKCS12", null), new TrustAllTrustManager()).createSSLServerSocketFactory()));
        InMemoryDirectoryServer imposteur = new InMemoryDirectoryServer(config);
        imposteur.startListening();
        try {
            LiaisonLdaps confiante = new LiaisonLdaps(new SSLUtil(new TrustStoreTrustManager(autre, NIP.toCharArray(), "PKCS12", true)).createSSLSocketFactory(), DELAI);
            assertThatThrownBy(() -> confiante.authentifier(annuaire("ldaps://localhost:" + imposteur.getListenPort() + "/" + BASE), "secret-service", "mroy", "bon-mot"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("connect error");
        } finally {
            imposteur.shutDown(true);
        }
    }

    @AfterAll
    static void arreter() {
        serveur.shutDown(true);
    }

    private static Annuaires.Annuaire annuaire(String url) {
        return Annuaires.valider("ann-ad", "T1", new Annuaires.Saisie("AD local", null, SERVICE, "ANNUAIRE_AD", List.of("urgences-sante.qc.ca"), "groups",
                List.of(new Annuaires.Correspondance("GEPS-Chefs", "manager")), null, true, "ldaps", url, false), Set.of("manager"));
    }

    private static Annuaires.Annuaire ad() {
        return annuaire("ldaps://localhost:" + serveur.getListenPort() + "/ou=personnel," + BASE);
    }

    private final LiaisonLdaps liaison = new LiaisonLdaps(confiance, DELAI);

    @Test
    @DisplayName("le bon mot de passe : la personne, son courriel en minuscules, ses groupes sous leur DN et leur CN")
    void connexionAdmise() {
        Annuaires.Personne p = liaison.authentifier(ad(), "secret-service", "mroy", "bon-mot").orElseThrow();
        assertThat(p.courriel()).isEqualTo("marie.roy@urgences-sante.qc.ca");
        assertThat(p.groupes()).containsExactly("CN=GEPS-Chefs,OU=Groupes,DC=us,DC=local", "GEPS-Chefs", "OU=Sans-CN,DC=us,DC=local", "pas un DN");
        assertThat(liaison.authentifier(ad(), "secret-service", "Marie.Roy@Urgences-Sante.qc.ca", "bon-mot")).isPresent();
    }

    @Test
    @DisplayName("refus sans dire pourquoi : mauvais mot de passe, mot de passe vide (liaison anonyme), inconnu, ambigu, sans courriel, filtre forcé")
    void refus() {
        Annuaires.Annuaire a = ad();
        assertThat(List.of(liaison.authentifier(a, "secret-service", "mroy", "mauvais"),
                liaison.authentifier(a, "secret-service", "mroy", ""),
                liaison.authentifier(a, "secret-service", "mroy", null),
                liaison.authentifier(a, "secret-service", " ", "bon-mot"),
                liaison.authentifier(a, "secret-service", null, "bon-mot"),
                liaison.authentifier(a, "secret-service", "x".repeat(257), "bon-mot"),
                liaison.authentifier(a, "secret-service", "inconnu", "bon-mot"),
                liaison.authentifier(a, "secret-service", "double", "x"),
                liaison.authentifier(a, "secret-service", "triple", "x"),
                liaison.authentifier(a, "secret-service", "sanscourriel", "x"),
                liaison.authentifier(a, "secret-service", "*", "bon-mot"),
                liaison.authentifier(a, "secret-service", "mroy)(uid=*", "bon-mot"))).allMatch(Optional::isEmpty);
    }

    @Test
    @DisplayName("un compte de service refusé, un annuaire injoignable, une autorité non reconnue ou une URL illisible : une panne, jamais un refus d'usager")
    void pannes() {
        assertThatThrownBy(() -> liaison.authentifier(ad(), "mauvais-secret", "mroy", "bon-mot")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid credentials");
        assertThatThrownBy(() -> liaison.authentifier(annuaire("ldaps://localhost:1/" + BASE), "secret-service", "mroy", "bon-mot"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("localhost");
        assertThatThrownBy(() -> liaison.authentifier(annuaire("ldaps://localhost:" + serveur.getListenPort() + "/ou=absente," + BASE), "secret-service",
                "mroy", "bon-mot")).isInstanceOf(IllegalStateException.class).hasMessageContaining("no such object");
        assertThatThrownBy(() -> liaison.authentifier(ad(), "secret-service", "panne", "x")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable");
        LiaisonLdaps sansConfiance = new LiaisonLdaps((SSLSocketFactory) SSLSocketFactory.getDefault(), DELAI);
        assertThatThrownBy(() -> sansConfiance.authentifier(ad(), "secret-service", "mroy", "bon-mot")).isInstanceOf(IllegalStateException.class);
        Annuaires.Annuaire illisible = new Annuaires.Annuaire("x", "T1", "X", "", SERVICE, "ANNUAIRE_AD", List.of("a.ca"), Annuaires.Revendication.GROUPES,
                List.of(), Optional.of("manager"), true, Annuaires.Fournisseur.LDAPS, "ldaps://localhost/(((", false);
        assertThatThrownBy(() -> liaison.authentifier(illisible, "secret-service", "mroy", "bon-mot")).isInstanceOf(IllegalStateException.class)
                .hasMessage("URL LDAPS illisible");
    }
}

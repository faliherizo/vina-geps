package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DestinataireIndisponibleException;
import ca.vina.nexu.effectifs.domaine.DestinationIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Acceptee;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.AdresseSftp;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConfigConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.DossierEmploye;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Mode;
import ca.vina.nexu.effectifs.domaine.SourceDossiersIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceHorodatagesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceIndisponibleException;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le mode SFTP des intégrations, contre un <strong>vrai serveur SFTP</strong> (Apache MINA SSHD, en mémoire du
 * processus, sur un répertoire temporaire) joint par le vrai client : clé de compte, empreinte d'hôte épinglée,
 * dépôt atomique, cueillette des lots. Les ports passent la suite de contrat commune (LSP).
 */
@DisplayName("Intégrations des effectifs — SFTP (dépôt et cueillette des lots)")
class IntegrationsSftpTest {

    private static final String COMPTE = "nexu";
    private static final KeyPair CLE_DU_COMPTE = paire();
    private static final KeyPair AUTRE_CLE = paire();

    @TempDir
    Path dossier;
    private Path racine;
    private SshServer serveur;
    private String empreinte;

    private static KeyPair paire() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** La clé privée telle qu'une variable d'environnement la porterait : PEM PKCS#8. */
    static String pem(KeyPair k) {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(k.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    @BeforeEach
    void demarrer() throws Exception {
        racine = Files.createDirectories(dossier.resolve("racine"));
        serveur = SshServer.setUpDefaultServer();
        serveur.setHost("127.0.0.1");
        serveur.setPort(0);
        serveur.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(dossier.resolve("hote.ser")));
        serveur.setPublickeyAuthenticator((compte, cle, session) -> COMPTE.equals(compte) && KeyUtils.compareKeys(cle, CLE_DU_COMPTE.getPublic()));
        serveur.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        serveur.setFileSystemFactory(new VirtualFileSystemFactory(racine));
        serveur.start();
        empreinte = KeyUtils.getFingerPrint(BuiltinDigests.sha256, serveur.getKeyPairProvider().loadKeys(null).iterator().next().getPublic());
    }

    @AfterEach
    void arreter() throws IOException {
        serveur.stop(true);
    }

    private ClientSftpIntegration.ConfigSftp config(String repertoire) {
        return new ClientSftpIntegration.ConfigSftp(new AdresseSftp(COMPTE, "127.0.0.1", serveur.getPort(), repertoire, empreinte), pem(CLE_DU_COMPTE));
    }

    private void lot(String sousDossier, String nom, Object contenu) throws IOException {
        Path d = Files.createDirectories(racine.resolve("depot").resolve(sousDossier));
        Files.writeString(d.resolve(nom), JsonMapper.builder().build().writeValueAsString(contenu));
    }

    // ── La suite de contrat commune ──────────────────────────────────────────

    @Nested
    @DisplayName("contrat DestinationIntegrationPort — SFTP")
    class DestinationSftp extends ContratIntegrationsEffectifsTest.ContratDestination {
        @Override
        DestinationIntegrationPort fabrique() {
            return new DestinationSftpIntegration(config("/depot"));
        }
    }

    @Nested
    @DisplayName("contrat SourceHorodatagesIntegrationPort — SFTP")
    class HorodatagesSftp extends ContratIntegrationsEffectifsTest.ContratHorodatages {
        @BeforeEach
        void deposer() throws IOException {
            lot("horodatages", "rao-2.json", List.of(ContratIntegrationsEffectifsTest.JEU.get(1)));
            lot("horodatages", "rao-1.json", List.of(ContratIntegrationsEffectifsTest.JEU.get(0)));
        }

        @Override
        SourceHorodatagesIntegrationPort fabrique() {
            return LotsSftpIntegration.horodatages(config("/depot"));
        }
    }

    @Nested
    @DisplayName("contrat SourceDossiersIntegrationPort — SFTP")
    class DossiersSftp extends ContratIntegrationsEffectifsTest.ContratDossiers {
        @BeforeEach
        void deposer() throws IOException {
            lot("dossiers", "corpo.json", ContratIntegrationsEffectifsTest.DOSSIERS);
        }

        @Override
        SourceDossiersIntegrationPort fabrique() {
            return LotsSftpIntegration.dossiers(config("/depot/"));
        }
    }

    // ── Ce que le contrat ne couvre pas ──────────────────────────────────────

    @Test
    @DisplayName("le lot déposé porte le message entier, sous le nom de sa clé, sans fichier partiel laissé derrière")
    void depot() throws IOException {
        Acceptee a = (Acceptee) new DestinationSftpIntegration(config("/depot")).livrer(ContratIntegrationsEffectifsTest.message("../lot 7"));
        assertThat(a.refExterne()).isEqualTo("SFTP:/depot/lot-paie/__lot_7.json");
        Path depose = racine.resolve("depot/lot-paie/__lot_7.json");
        assertThat(JsonMapper.builder().build().readValue(Files.readString(depose), Map.class))
                .isEqualTo(Map.of("id", "m-../lot 7", "type", "LOT_PAIE", "cle", "../lot 7", "charge", Map.of("lignes", List.of(Map.of("codePaie", "100")))));
        try (Stream<Path> fichiers = Files.list(racine.resolve("depot/lot-paie"))) {
            assertThat(fichiers.map(p -> p.getFileName().toString())).containsExactly("__lot_7.json");
        }
        // Déjà déposé : le fichier n'est pas réécrit, même si la charge a changé.
        new DestinationSftpIntegration(config("/depot")).livrer(ContratIntegrationsEffectifsTest.message("../lot 7", Map.of("autre", 1)));
        assertThat(Files.readString(depose)).contains("codePaie");
    }

    @Test
    @DisplayName("un hôte dont l'empreinte diffère, une clé refusée ou illisible : le dépôt est une panne nommée")
    void refusDeConnexion() {
        AdresseSftp usurpe = new AdresseSftp(COMPTE, "127.0.0.1", serveur.getPort(), "/depot", "SHA256:" + "B".repeat(43));
        assertThatThrownBy(() -> new DestinationSftpIntegration(new ClientSftpIntegration.ConfigSftp(usurpe, pem(CLE_DU_COMPTE), Duration.ofSeconds(5)))
                .livrer(ContratIntegrationsEffectifsTest.message("k")))
                .isInstanceOf(DestinataireIndisponibleException.class).hasMessageStartingWith("PFT injoignable en SFTP : ");
        AdresseSftp vraie = new AdresseSftp(COMPTE, "127.0.0.1", serveur.getPort(), "/depot", empreinte);
        assertThatThrownBy(() -> new DestinationSftpIntegration(new ClientSftpIntegration.ConfigSftp(vraie, pem(AUTRE_CLE), Duration.ofSeconds(5)))
                .livrer(ContratIntegrationsEffectifsTest.message("k")))
                .isInstanceOf(DestinataireIndisponibleException.class);
        assertThatThrownBy(() -> new DestinationSftpIntegration(new ClientSftpIntegration.ConfigSftp(vraie, "pas une clé")).livrer(ContratIntegrationsEffectifsTest.message("k")))
                .isInstanceOf(DestinataireIndisponibleException.class).hasMessage("PFT injoignable en SFTP : clé privée du connecteur illisible");
        assertThat(racine.resolve("depot")).doesNotExist();
    }

    @Test
    @DisplayName("une clé PEM mal formée est illisible, quelle que soit la forme de l'erreur")
    void clesIllisibles() {
        for (String pem : List.of("", "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n")) {
            assertThatThrownBy(() -> ClientSftpIntegration.cle(pem)).isInstanceOf(IOException.class);
        }
    }

    @Test
    @DisplayName("la cueillette ignore les dépôts en cours, les fichiers cachés et les répertoires ; le lot le plus récent l'emporte")
    void cueillette() throws IOException {
        lot("dossiers", "a.json", List.of(Map.of("matricule", "P1", "anciennete", 4)));
        lot("dossiers", "b.json", List.of(Map.of("matricule", "P1", "anciennete", 5)));
        lot("dossiers", ".c.json.part", List.of(Map.of("matricule", "P1", "anciennete", 99)));
        lot("dossiers", ".cache.json", List.of(Map.of("matricule", "P1", "anciennete", 98)));
        lot("dossiers", "notes.txt", "rien");
        Files.createDirectories(racine.resolve("depot/dossiers/archive.json"));
        assertThat(LotsSftpIntegration.dossiers(config("/depot")).lire(List.of("P1"))).containsExactly(new DossierEmploye("P1", 5));
        // Aucun répertoire de lots : rien à cueillir, sans erreur.
        assertThat(LotsSftpIntegration.horodatages(config("/depot")).lire("1970-01-01T00:00:00.000Z")).isEmpty();
        // Un horodatage sans instant n'est jamais rendu : le curseur ne saurait où le placer.
        lot("horodatages", "rao.json", List.of(Map.of("cle", "x", "matricule", "P1")));
        assertThat(LotsSftpIntegration.horodatages(config("/depot")).lire("1970")).isEmpty();
    }

    @Test
    @DisplayName("un lot qui n'est pas une liste, ou un serveur injoignable : la source est indisponible")
    void sourceIndisponible() throws IOException {
        lot("dossiers", "faux.json", Map.of("dossiers", List.of()));
        assertThatThrownBy(() -> LotsSftpIntegration.dossiers(config("/depot")).lire(List.of("P1")))
                .isInstanceOf(SourceIndisponibleException.class).hasMessage("un lot de « dossiers » n'est pas une liste JSON");
        int port = serveur.getPort();
        serveur.stop(true);
        ClientSftpIntegration.ConfigSftp ferme = new ClientSftpIntegration.ConfigSftp(new AdresseSftp(COMPTE, "127.0.0.1", port, "/depot", empreinte), pem(CLE_DU_COMPTE),
                Duration.ofSeconds(2));
        assertThatThrownBy(() -> LotsSftpIntegration.horodatages(ferme).lire("1970"))
                .isInstanceOf(SourceIndisponibleException.class).hasMessageStartingWith("source SFTP injoignable : ");
    }

    @Test
    @DisplayName("une erreur du serveur autre que « fichier absent » n'est pas prise pour une absence")
    void permissionRefusee() {
        SftpClient refuse = (SftpClient) Proxy.newProxyInstance(SftpClient.class.getClassLoader(), new Class<?>[]{SftpClient.class}, (o, m, args) -> {
            throw new SftpException(SftpConstants.SSH_FX_PERMISSION_DENIED, "permission refusée");
        });
        assertThatThrownBy(() -> ClientSftpIntegration.attributs(refuse, "/interdit/x")).isInstanceOf(SftpException.class).hasMessage("permission refusée");
        assertThat(ClientSftpIntegration.joindre("/", "a")).isEqualTo("/a");
        assertThat(ClientSftpIntegration.joindre("/d", "a")).isEqualTo("/d/a");
        assertThat(ClientSftpIntegration.nomDeFichier("PFT|2031-01|lot")).isEqualTo("PFT_2031-01_lot.json");
        assertThat(new ClientSftpIntegration.ConfigSftp(null, "x").delai()).isEqualTo(ClientSftpIntegration.DELAI);
    }

    @Test
    @DisplayName("registre : le mode SFTP rend les adaptateurs SFTP, la clé lue dans l'environnement au moment de l'appel")
    void registre() throws IOException {
        String url = "sftp://" + COMPTE + "@127.0.0.1:" + serveur.getPort() + "/depot?empreinte=" + empreinte;
        RegistreConnecteursIntegration registre = new RegistreConnecteursIntegration(
                nom -> "ESB_CLE_SFTP".equals(nom) ? Optional.of(pem(CLE_DU_COMPTE)) : Optional.empty(), r -> new ClientHttpIntegration.Reponse(500, ""));
        ConnecteurEnregistre esb = new ConnecteurEnregistre(CodeConnecteur.ESB, new ConfigConnecteur(Mode.SFTP, url, "ESB_CLE_SFTP", true), List.of(), null);
        assertThat(registre.destination("t1", esb)).isInstanceOf(DestinationSftpIntegration.class);
        lot("dossiers", "corpo.json", ContratIntegrationsEffectifsTest.DOSSIERS);
        assertThat(registre.dossiers("t1", esb).lire(List.of("P1"))).containsExactly(new DossierEmploye("P1", 12));
        assertThat(registre.horodatages("t1", esb).lire("1970")).isEmpty();
        ConnecteurEnregistre sansCle = new ConnecteurEnregistre(CodeConnecteur.ESB, new ConfigConnecteur(Mode.SFTP, url, null, true), List.of(), null);
        assertThatThrownBy(() -> registre.destination("t1", sansCle)).isInstanceOf(IllegalStateException.class)
                .hasMessage("connecteur ESB en mode SFTP sans clé privée");
        assertThat(IntegrationsEffectifs.adresseSftp(url).port()).isEqualTo(serveur.getPort());
    }

    @Test
    @DisplayName("le client SFTP rend ce que l'opération rend")
    void operation() throws IOException {
        Files.writeString(racine.resolve("present.json"), "[]");
        assertThat(ClientSftpIntegration.executer(config("/"), (SftpClient sftp, String rep) -> ClientSftpIntegration.attributs(sftp, "/present.json").isPresent())).isTrue();
    }
}

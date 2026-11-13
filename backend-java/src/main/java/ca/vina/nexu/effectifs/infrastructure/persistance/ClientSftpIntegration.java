package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.AdresseSftp;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Ce que les adaptateurs SFTP des intégrations partagent : une session authentifiée par clé, contre un hôte dont
 * l'empreinte est <strong>épinglée</strong>, et les gestes de fichier d'un lot. Un fichier se dépose sous un nom
 * temporaire puis se renomme : le système d'en face ne voit jamais un lot à moitié écrit.
 */
public final class ClientSftpIntegration {

    /** Au-delà, le serveur est réputé injoignable : l'ordonnanceur retentera. */
    public static final Duration DELAI = Duration.ofSeconds(15);
    private static final String SUFFIXE_JSON = ".json";
    private static final String SUFFIXE_PARTIEL = ".part";

    private ClientSftpIntegration() {
    }

    /** L'adresse du serveur, la clé privée du compte (texte PEM, lue au moment de l'appel) et le délai. */
    public record ConfigSftp(AdresseSftp adresse, String clePrivee, Duration delai) {
        public ConfigSftp(AdresseSftp adresse, String clePrivee) {
            this(adresse, clePrivee, DELAI);
        }
    }

    /** Ce qu'on fait une fois connecté ; le répertoire de dépôt du compte est fourni. */
    @FunctionalInterface
    public interface Operation<T> {
        T executer(SftpClient sftp, String repertoire) throws IOException;
    }

    public static <T> T executer(ConfigSftp c, Operation<T> operation) throws IOException {
        AdresseSftp a = c.adresse();
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.setServerKeyVerifier(epingle(a.empreinte()));
            client.start();
            try (ClientSession session = client.connect(a.compte(), a.hote(), a.port()).verify(c.delai()).getSession()) {
                session.addPublicKeyIdentity(cle(c.clePrivee()));
                session.auth().verify(c.delai());
                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    return operation.executer(sftp, a.repertoire());
                }
            }
        }
    }

    /** L'hôte n'est admis que si sa clé a l'empreinte configurée. */
    static ServerKeyVerifier epingle(String empreinte) {
        return (session, adresse, cle) -> empreinte.equals(KeyUtils.getFingerPrint(BuiltinDigests.sha256, cle));
    }

    static KeyPair cle(String pem) throws IOException {
        try (InputStream flux = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))) {
            Iterable<KeyPair> cles = SecurityUtils.loadKeyPairIdentities(null, NamedResource.ofName("cle-du-connecteur"), flux, null);
            Iterator<KeyPair> it = cles == null ? null : cles.iterator();
            if (it == null || !it.hasNext()) {
                throw new IOException("clé privée du connecteur illisible");
            }
            return it.next();
        } catch (GeneralSecurityException e) {
            throw new IOException("clé privée du connecteur illisible", e);
        }
    }

    static String joindre(String repertoire, String nom) {
        return repertoire.endsWith("/") ? repertoire + nom : repertoire + "/" + nom;
    }

    /** Une clé d'idempotence devient un nom de fichier sûr : ni barre, ni point de départ, ni caractère de contrôle. */
    static String nomDeFichier(String cle) {
        return cle.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "_") + SUFFIXE_JSON;
    }

    static Optional<SftpClient.Attributes> attributs(SftpClient sftp, String chemin) throws IOException {
        try {
            return Optional.of(sftp.stat(chemin));
        } catch (SftpException e) {
            if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Comme {@code mkdir -p} : chaque niveau manquant est créé, un niveau présent est laissé tel quel. */
    static void creerRepertoire(SftpClient sftp, String chemin) throws IOException {
        StringBuilder courant = new StringBuilder();
        for (String niveau : chemin.split("/")) {
            if (niveau.isEmpty()) {
                continue;
            }
            courant.append('/').append(niveau);
            if (attributs(sftp, courant.toString()).isEmpty()) {
                sftp.mkdir(courant.toString());
            }
        }
    }

    /** Dépose sous un nom temporaire, puis renomme : le fichier apparaît entier ou pas du tout. */
    static void deposer(SftpClient sftp, String repertoire, String nom, byte[] contenu) throws IOException {
        String partiel = joindre(repertoire, "." + nom + SUFFIXE_PARTIEL);
        try (OutputStream sortie = sftp.write(partiel)) {
            sortie.write(contenu);
        }
        sftp.rename(partiel, joindre(repertoire, nom));
    }

    /** Les fichiers de lot complets : {@code *.json}, jamais un dépôt en cours ({@code .x.json.part}) ni un fichier caché. */
    private static List<String> lots(SftpClient sftp, String repertoire) throws IOException {
        List<String> noms = new ArrayList<>();
        for (SftpClient.DirEntry e : sftp.readDir(repertoire)) {
            if (e.getFilename().endsWith(SUFFIXE_JSON) && !e.getFilename().startsWith(".") && e.getAttributes().isRegularFile()) {
                noms.add(e.getFilename());
            }
        }
        noms.sort(String::compareTo);
        return noms;
    }

    /** Le contenu des fichiers {@code *.json} d'un répertoire, par nom croissant ; un répertoire absent n'a rien à lire. */
    static List<String> lireFichiers(SftpClient sftp, String repertoire) throws IOException {
        if (attributs(sftp, repertoire).isEmpty()) {
            return List.of();
        }
        List<String> contenus = new ArrayList<>();
        for (String nom : lots(sftp, repertoire)) {
            try (InputStream entree = sftp.read(joindre(repertoire, nom))) {
                contenus.add(new String(entree.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return contenus;
    }
}

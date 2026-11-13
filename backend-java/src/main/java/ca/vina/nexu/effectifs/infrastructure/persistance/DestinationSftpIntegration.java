package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DestinataireIndisponibleException;
import ca.vina.nexu.effectifs.domaine.DestinationIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Acceptee;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Issue;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageALivrer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Le destinataire SFTP d'un connecteur sortant : chaque message devient un fichier de lot
 * {@code {repertoire}/{type}/{cle}.json}. Le nom est la clé d'idempotence : un message déjà déposé n'est pas réécrit.
 * Une panne de transport, d'authentification ou d'empreinte lève : l'ordonnanceur retentera, puis le message passera en échec.
 */
public class DestinationSftpIntegration implements DestinationIntegrationPort {

    private static final String PREFIXE_REFERENCE = "SFTP:";

    private final ClientSftpIntegration.ConfigSftp config;

    public DestinationSftpIntegration(ClientSftpIntegration.ConfigSftp config) {
        this.config = config;
    }

    @Override
    public Issue livrer(MessageALivrer m) {
        String nom = ClientSftpIntegration.nomDeFichier(m.cle());
        try {
            return ClientSftpIntegration.executer(config, (sftp, racine) -> {
                String dossier = ClientSftpIntegration.joindre(racine, m.type().chemin().substring(1));
                ClientSftpIntegration.creerRepertoire(sftp, dossier);
                String chemin = ClientSftpIntegration.joindre(dossier, nom);
                if (ClientSftpIntegration.attributs(sftp, chemin).isEmpty()) {
                    ClientSftpIntegration.deposer(sftp, dossier, nom, contenu(m));
                }
                return new Acceptee(PREFIXE_REFERENCE + chemin);
            });
        } catch (IOException e) {
            throw new DestinataireIndisponibleException(m.connecteur() + " injoignable en SFTP : " + ClientHttpIntegration.motif(e));
        }
    }

    private static byte[] contenu(MessageALivrer m) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("id", m.id());
        corps.put("type", m.type());
        corps.put("cle", m.cle());
        corps.put("charge", m.charge());
        return ClientHttpIntegration.json(corps).getBytes(StandardCharsets.UTF_8);
    }
}

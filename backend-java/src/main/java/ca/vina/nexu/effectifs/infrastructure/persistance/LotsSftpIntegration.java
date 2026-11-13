package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.SourceDossiersIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceHorodatagesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceIndisponibleException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les sources entrantes en SFTP : le système d'en face dépose des fichiers de lot, chacun une liste JSON, dans
 * {@code {repertoire}/horodatages/} (RAO) ou {@code {repertoire}/dossiers/} (BD Corpo) ; l'application les cueille.
 * Les fichiers ne sont ni déplacés ni effacés : le curseur du connecteur dit où la lecture en est.
 */
public final class LotsSftpIntegration {

    private static final String HORODATAGES = "horodatages";
    private static final String DOSSIERS = "dossiers";

    private LotsSftpIntegration() {
    }

    /** Les horodatages postérieurs au curseur (exclu), dans l'ordre du temps. */
    public static SourceHorodatagesIntegrationPort horodatages(ClientSftpIntegration.ConfigSftp config) {
        return depuis -> elements(config, HORODATAGES).stream().map(IntegrationsEffectifs::horodatageDe)
                .filter(h -> h.horodatage() != null && h.horodatage().compareTo(depuis) > 0)
                .sorted(Comparator.comparing(IntegrationsEffectifs.HorodatageExterne::horodatage)).toList();
    }

    /** Les dossiers des matricules demandés ; quand deux lots parlent du même matricule, le plus récent (par nom) l'emporte. */
    public static SourceDossiersIntegrationPort dossiers(ClientSftpIntegration.ConfigSftp config) {
        return matricules -> {
            Set<String> voulus = Set.copyOf(matricules);
            Map<String, IntegrationsEffectifs.DossierEmploye> parMatricule = new LinkedHashMap<>();
            elements(config, DOSSIERS).stream().map(IntegrationsEffectifs::dossierDe).filter(d -> voulus.contains(d.matricule()))
                    .forEach(d -> parMatricule.put(d.matricule(), d));
            return List.copyOf(parMatricule.values());
        };
    }

    private static List<Object> elements(ClientSftpIntegration.ConfigSftp config, String dossier) {
        List<String> contenus;
        try {
            contenus = ClientSftpIntegration.executer(config, (sftp, racine) -> ClientSftpIntegration.lireFichiers(sftp, ClientSftpIntegration.joindre(racine, dossier)));
        } catch (IOException e) {
            throw new SourceIndisponibleException("source SFTP injoignable : " + ClientHttpIntegration.motif(e));
        }
        List<Object> elements = new ArrayList<>();
        for (String contenu : contenus) {
            if (!(ClientHttpIntegration.lu(contenu) instanceof List<?> lot)) {
                throw new SourceIndisponibleException("un lot de « " + dossier + " » n'est pas une liste JSON");
            }
            elements.addAll(lot);
        }
        return elements;
    }
}

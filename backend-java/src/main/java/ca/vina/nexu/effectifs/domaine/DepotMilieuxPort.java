package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Port de persistance des milieux et de leurs preuves. */
public interface DepotMilieuxPort {

    Optional<Milieu> lire(String tenantId, String id);

    List<Milieu> lister(String tenantId);

    boolean codeExiste(String tenantId, String code);

    /** Champs : libellé, type, unité, adresse, coordonnées (nulles = aucune), actif. */
    record Champs(String libelle, String typeCode, String uniteCode, String adresse, Milieux.Coordonnees coordonnees, boolean actif) { }

    void inserer(String tenantId, String id, String code, Champs c, String creeLe);

    void modifier(String tenantId, String id, Champs c);

    /**
     * Remplace les preuves. Le secret d'un code QR se conserve tant que le mécanisme reste — le
     * régénérer invaliderait les affiches imprimées ; sinon {@code nouveauSecret} en tire un.
     */
    void remplacerPreuves(String tenantId, String milieuId, List<Milieux.Preuve> preuves, Supplier<String> nouveauSecret);

    /** Le secret du code QR d'un milieu, pour le pointage ; jamais exposé par une lecture. */
    Optional<String> secretQr(String tenantId, String milieuId);
}

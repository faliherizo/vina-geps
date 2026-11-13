package ca.vina.nexu.auth.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ca.vina.nexu.auth.domaine.PhotoProfil;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * **La photo de profil** : la sienne, posée, relue et retirée par chacun ; celle d'un collègue,
 * servie au même établissement seulement.
 *
 * L'écriture ne s'atteint qu'après l'antivirus, posé par le contrôleur avant l'appel : le dépôt
 * enregistre ce qui lui arrive, il ne décide pas de ce qui est sain.
 *
 * La table est celle de la migration 0163 ; Liquibase est en constat (voir CLAUDE.md), le schéma
 * vient donc du Node et rien n'est créé ici.
 */
@Repository
public class DepotPhotosProfil {

    /** Ce qu'il faut pour écrire une réponse HTTP : le type déclaré et les octets. */
    public record Photo(String typeMime, byte[] contenu) {
    }

    private final JdbcClient jdbc;

    public DepotPhotosProfil(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * La photo d'un compte, si elle existe **et** si le demandeur est du même établissement.
     *
     * Les deux conditions sont jugées ensemble, en une requête : les séparer laisserait le
     * cloisonnement à l'appelant, et un appelant finit par l'oublier. Un compte global — sans
     * établissement — ne satisfait jamais la condition : il administre la plateforme, il ne
     * consulte pas les visages.
     */
    public Optional<Photo> photoDUnCollegue(String demandeurId, String cibleId) {
        return jdbc.sql("""
                        SELECT p.typeMime AS typeMime, p.contenu AS contenu
                          FROM auth_photos p
                          JOIN users cible     ON cible.id = p.userId
                          JOIN users demandeur ON demandeur.id = ?
                         WHERE p.userId = ?
                           AND cible.tenantId IS NOT NULL
                           AND cible.tenantId = demandeur.tenantId
                        """)
                .params(demandeurId, cibleId)
                .query().listOfRows().stream().findFirst()
                .map(DepotPhotosProfil::enPhoto);
    }

    /** La photo du compte lui-même, telle qu'elle a été posée. */
    public Optional<PhotoProfil.Photo> lire(String userId) {
        return jdbc.sql("SELECT typeMime, contenu, taille, modifieLe FROM auth_photos WHERE userId = ?").param(userId)
                .query((rs, i) -> new PhotoProfil.Photo(rs.getString("typeMime"), rs.getString("contenu"), rs.getInt("taille"), rs.getString("modifieLe")))
                .optional();
    }

    /** Pose ou remplace : un compte n'a qu'une photo. */
    public PhotoProfil.Photo poser(String userId, PhotoProfil.Saisie s, String le) {
        jdbc.sql("""
                        INSERT INTO auth_photos (userId, typeMime, taille, contenu, modifieLe) VALUES (?,?,?,?,?)
                        ON CONFLICT(userId) DO UPDATE SET typeMime = excluded.typeMime, taille = excluded.taille,
                          contenu = excluded.contenu, modifieLe = excluded.modifieLe
                        """)
                .params(userId, s.typeMime(), s.octets().length, s.contenu(), le).update();
        return new PhotoProfil.Photo(s.typeMime(), s.contenu(), s.octets().length, le);
    }

    /** Faux s'il n'y en avait pas : un retrait sans objet n'est pas une faute. */
    public boolean retirer(String userId) {
        return jdbc.sql("DELETE FROM auth_photos WHERE userId = ?").param(userId).update() > 0;
    }

    private static Photo enPhoto(Map<String, Object> ligne) {
        return new Photo(String.valueOf(ligne.get("typeMime")),
                Base64.getDecoder().decode(String.valueOf(ligne.get("contenu"))));
    }
}

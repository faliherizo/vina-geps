package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.domaine.PhotoProfil;
import ca.vina.nexu.auth.infrastructure.persistance.DepotPhotosProfil;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.securite.domaine.Antivirus;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * **La photo d'un collègue**, servie telle quelle au {@code <img>} qui la demande.
 *
 * Trois décisions, et chacune répond à une question que « servir une photo » pose :
 *
 * <ul>
 *   <li><b>À qui ?</b> Aux comptes du même établissement, et à eux seuls — la photo est un
 *       renseignement personnel (Loi 25), et le cloisonnement par établissement est la frontière
 *       que tout le reste du produit respecte déjà.</li>
 *   <li><b>Comment ?</b> En octets, avec son type. Un {@code <img>} ne sait pas lire du JSON, et
 *       charger chaque photo en base64 dans l'écran en chargerait trente pour trente
 *       conversations — là où le navigateur sait les mettre en cache tout seul.</li>
 *   <li><b>Et si elle n'existe pas ?</b> <b>404</b>, jamais une silhouette de remplacement :
 *       l'écran dessine alors les initiales, qui disent au moins de qui il s'agit.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class ControleurPhotoProfil {

    /** Privée et courte : la photo suit le compte connecté, et un changement se voit dans la minute. */
    private static final String CACHE_PRIVE_UNE_MINUTE = "private, max-age=60";
    private static final String ABSENTE = "aucune photo de profil";

    private final DepotPhotosProfil photos;
    private final GardeAcces garde;
    private final Antivirus.AnalyseurFichier antivirus;
    private final Clock horloge;

    public ControleurPhotoProfil(DepotPhotosProfil photos, GardeAcces garde, Antivirus.AnalyseurFichier antivirus, Clock horloge) {
        this.photos = photos;
        this.garde = garde;
        this.antivirus = antivirus;
        this.horloge = horloge;
    }

    private static ResponseEntity<Object> erreur(int statut, String message) {
        return ResponseEntity.status(statut).body(Map.of("errors", List.of(message)));
    }

    /*
     * Chacun pose et retire la sienne, et personne d'autre : une photo imposée ou retirée par un
     * tiers n'est plus un choix, et le retrait doit rester à portée de celui qu'elle représente
     * (Loi 25 — le consentement se retire). La cible est toujours le compte connecté.
     */

    @GetMapping("/me/photo")
    public ResponseEntity<Object> maPhoto(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        return photos.lire(GardeAcces.contexte(requete).userId())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> erreur(404, ABSENTE));
    }

    /** L'antivirus passe avant l'écriture, comme pour toute pièce reçue ; tout refus est un 400 nommé. */
    @PutMapping("/me/photo")
    public ResponseEntity<Object> poser(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        Map<String, Object> c = corps == null ? Map.of() : corps;
        try {
            PhotoProfil.Saisie s = PhotoProfil.valider(c.get("typeMime"), c.get("contenu"));
            Antivirus.exigerSain(antivirus, s.octets());
            PhotoProfil.Photo p = photos.poser(GardeAcces.contexte(requete).userId(), s, Iso.de(horloge.instant()));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("typeMime", p.typeMime());
            r.put("taille", p.taille());
            r.put("modifieLe", p.modifieLe());
            return ResponseEntity.ok(r);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return erreur(400, e.getMessage());
        }
    }

    @DeleteMapping("/me/photo")
    public ResponseEntity<Object> retirer(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        return ResponseEntity.ok(Map.of("retiree", photos.retirer(GardeAcces.contexte(requete).userId())));
    }

    @GetMapping("/personnes/{userId}/photo")
    public ResponseEntity<Object> photo(HttpServletRequest requete, @PathVariable String userId) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        String demandeur = GardeAcces.contexte(requete).userId();
        // Un 404 plutôt qu'un 403 : répondre « interdit » apprendrait que le compte existe ailleurs.
        return photos.photoDUnCollegue(demandeur, userId)
                .<ResponseEntity<Object>>map(p -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, p.typeMime())
                        // Le type est celui qu'a validé le dépôt ; `nosniff` empêche le navigateur
                        // de le redeviner — un fichier admis comme image et contenant du HTML
                        // s'exécuterait sinon dans la page.
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CACHE_CONTROL, CACHE_PRIVE_UNE_MINUTE)
                        .body(p.contenu()))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("errors", List.of(ABSENTE))));
    }
}

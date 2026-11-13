package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotRessourcesPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.partage.personnel.FichesPersonnelPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Les écritures des ressources — jumeau de {@code serviceRessources.ts}. Chaque attribut renvoie à un
 * référentiel en vigueur ; rien n'est une liste codée.
 */
public class GestionnaireRessources {

    private static final Pattern MATRICULE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");

    private final DepotRessourcesPort depot;
    private final LectureRessources lecture;
    private final LectureReferentiels referentiels;
    private final LecturePerimetres perimetres;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;
    private final FichesPersonnelPort fiches;

    public GestionnaireRessources(FichesPersonnelPort fiches, DepotRessourcesPort depot, LectureRessources lecture, LectureReferentiels referentiels, LecturePerimetres perimetres,
                                  JournalEffectifsPort journal, Sequence sequence) {
        this.depot = depot;
        this.lecture = lecture;
        this.referentiels = referentiels;
        this.perimetres = perimetres;
        this.journal = journal;
        this.sequence = sequence;
        this.fiches = fiches;
    }

    /** Une ressource reliée à un compte a sa fiche au répertoire du personnel ; sans compte, il n'y a personne à aviser. */
    private void inscrire(Contexte ctx, String id, DepotRessourcesPort.Champs c) {
        if (c.utilisateurId() != null) {
            fiches.inscrire(ctx.tenantId(), new FichesPersonnelPort.Inscription(id, c.nom(), c.utilisateurId()));
        }
    }

    static String texte(Object v) {
        return v instanceof String s ? s.trim() : "";
    }

    private String facultatif(Contexte ctx, TypeReferentiel type, Object v) {
        String c = texte(v);
        if (c.isEmpty()) {
            return null;
        }
        referentiels.exigerValeur(ctx.tenantId(), type, c, ctx.aujourdhui());
        return c;
    }

    private static double ancienneteDe(Object v) {
        if (v == null || "".equals(v)) {
            return 0;
        }
        double a = PlanificationEntrees.nombre(v);
        if (Double.isNaN(a) || Double.isInfinite(a) || a < 0) {
            throw new EffectifsInvalideException("« ancienneté » : nombre positif ou nul");
        }
        return a;
    }

    private DepotRessourcesPort.Champs champs(Contexte ctx, Map<String, Object> entree) {
        String nom = texte(entree.get("nom"));
        if (nom.isEmpty()) {
            throw new EffectifsInvalideException("« nom » est obligatoire");
        }
        String uniteCode = texte(entree.get("uniteCode"));
        String corpsEmploiCode = texte(entree.get("corpsEmploiCode"));
        referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.UNITES, uniteCode, ctx.aujourdhui());
        referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.CORPS_EMPLOI, corpsEmploiCode, ctx.aujourdhui());
        perimetres.exiger(ctx, uniteCode, Perimetres.Niveau.GESTION);
        String utilisateurId = texte(entree.get("utilisateurId"));
        return new DepotRessourcesPort.Champs(nom, uniteCode, corpsEmploiCode, ancienneteDe(entree.get("anciennete")),
                utilisateurId.isEmpty() ? null : utilisateurId, facultatif(ctx, TypeReferentiel.TITRES_EMPLOI, entree.get("titreEmploiCode")),
                facultatif(ctx, TypeReferentiel.STATUTS_EMPLOI, entree.get("statutEmploiCode")),
                facultatif(ctx, TypeReferentiel.CONVENTIONS, entree.get("conventionCode")));
    }

    public Ressource creer(Contexte ctx, Map<String, Object> entree) {
        String matricule = texte(entree.get("matricule"));
        if (!MATRICULE.matcher(matricule).matches()) {
            throw new EffectifsInvalideException("« matricule » : 1 à 40 lettres, chiffres, tirets ou soulignés");
        }
        DepotRessourcesPort.Champs c = champs(ctx, entree);
        if (depot.matriculeExiste(ctx.tenantId(), matricule)) {
            throw new ConflitException("matricule « " + matricule + " » déjà utilisé");
        }
        String id = sequence.suivant("eres");
        depot.inserer(ctx.tenantId(), id, matricule, c, ctx.maintenant());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("matricule", matricule);
        apres.put("champs", c);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("ressource.creee", "ressource", id, c.uniteCode(), null, apres));
        inscrire(ctx, id, c);
        return lecture.exiger(ctx, id);
    }

    public Ressource modifier(Contexte ctx, String id, Map<String, Object> entree) {
        Ressource avant = lecture.exiger(ctx, id);
        perimetres.exiger(ctx, avant.uniteCode(), Perimetres.Niveau.GESTION);
        DepotRessourcesPort.Champs c = champs(ctx, entree);
        Object brut = entree.get("actif");
        boolean actif = brut == null ? avant.actif() : Boolean.TRUE.equals(brut);
        depot.modifier(ctx.tenantId(), id, c, actif);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("ressource.modifiee", "ressource", id, c.uniteCode(), avant, Map.of("champs", c, "actif", actif)));
        inscrire(ctx, id, c);
        return lecture.exiger(ctx, id);
    }

    /** Pose le statut d'emploi d'une ressource — l'AGPH (lot R7) l'appelle quand un choix de poste attribue un statut. */
    public Ressource poserStatutEmploi(Contexte ctx, String id, String statutEmploiCode, String systeme) {
        Ressource avant = lecture.exiger(ctx, id);
        referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.STATUTS_EMPLOI, statutEmploiCode, ctx.aujourdhui());
        depot.poserStatutEmploi(ctx.tenantId(), id, statutEmploiCode);
        Map<String, Object> av = new LinkedHashMap<>();
        av.put("statutEmploiCode", avant.statutEmploiCode());
        journal.ecrire(ctx, new JournalEffectifs.Consignation("ressource.statut", "ressource", id, avant.uniteCode(), av,
                Map.of("statutEmploiCode", statutEmploiCode), systeme));
        return lecture.exiger(ctx, id);
    }
}

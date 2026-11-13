package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotMilieuxPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Milieu;
import ca.vina.nexu.effectifs.domaine.Milieux;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Les écritures des milieux et de leurs preuves — jumeau de {@code serviceMilieux.ts}. */
public class GestionnaireMilieux {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final int OCTETS_SECRET = 24;

    private final DepotMilieuxPort depot;
    private final LectureMilieux lecture;
    private final LectureReferentiels referentiels;
    private final LecturePerimetres perimetres;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public GestionnaireMilieux(DepotMilieuxPort depot, LectureMilieux lecture, LectureReferentiels referentiels, LecturePerimetres perimetres,
                               JournalEffectifsPort journal, Sequence sequence) {
        this.depot = depot;
        this.lecture = lecture;
        this.referentiels = referentiels;
        this.perimetres = perimetres;
        this.journal = journal;
        this.sequence = sequence;
    }

    private static String texte(Object v) {
        return v instanceof String s ? s.trim() : "";
    }

    private DepotMilieuxPort.Champs champs(Contexte ctx, Map<String, Object> entree, boolean actif) {
        String libelle = texte(entree.get("libelle"));
        if (libelle.isEmpty()) {
            throw new EffectifsInvalideException("« libellé » est obligatoire");
        }
        String typeCode = texte(entree.get("typeCode"));
        String uniteCode = texte(entree.get("uniteCode"));
        referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.TYPES_MILIEU, typeCode, ctx.aujourdhui());
        referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.UNITES, uniteCode, ctx.aujourdhui());
        perimetres.exiger(ctx, uniteCode, Perimetres.Niveau.GESTION);
        String adresse = texte(entree.get("adresse"));
        return new DepotMilieuxPort.Champs(libelle, typeCode, uniteCode, adresse.isEmpty() ? null : adresse, Milieux.coordonneesDe(entree), actif);
    }

    public Milieu creer(Contexte ctx, Map<String, Object> entree) {
        String code = texte(entree.get("code"));
        if (!CODE.matcher(code).matches()) {
            throw new EffectifsInvalideException("« code » : 1 à 40 lettres, chiffres, tirets ou soulignés");
        }
        DepotMilieuxPort.Champs c = champs(ctx, entree, true);
        if (depot.codeExiste(ctx.tenantId(), code)) {
            throw new ConflitException("milieu « " + code + " » existe déjà");
        }
        String id = sequence.suivant("emil");
        depot.inserer(ctx.tenantId(), id, code, c, ctx.maintenant());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("milieu.cree", "milieu", id, c.uniteCode(), null, Map.of("code", code, "champs", c)));
        return lecture.lire(ctx.tenantId(), id);
    }

    public Milieu modifier(Contexte ctx, String id, Map<String, Object> entree) {
        Milieu avant = lecture.lire(ctx.tenantId(), id);
        perimetres.exiger(ctx, avant.uniteCode(), Perimetres.Niveau.GESTION);
        Object actif = entree.get("actif");
        DepotMilieuxPort.Champs c = champs(ctx, entree, actif == null ? avant.actif() : Boolean.TRUE.equals(actif));
        if (c.coordonnees() == null && avant.preuves().stream().anyMatch(p -> p.mecanisme() == Milieux.Mecanisme.GEOREPERAGE)) {
            throw new EffectifsInvalideException("ce milieu exige le géorepérage : ses coordonnées ne peuvent pas être retirées");
        }
        depot.modifier(ctx.tenantId(), id, c);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("milieu.modifie", "milieu", id, c.uniteCode(), avant, c));
        return lecture.lire(ctx.tenantId(), id);
    }

    /** Remplace les preuves exigées ; le secret d'un code QR est conservé tant que le mécanisme reste. */
    @SuppressWarnings("unchecked")
    public Milieu definirPreuves(Contexte ctx, String id, Object entrees) {
        Milieu milieu = lecture.lire(ctx.tenantId(), id);
        perimetres.exiger(ctx, milieu.uniteCode(), Perimetres.Niveau.GESTION);
        if (!(entrees instanceof List<?> l)) {
            throw new EffectifsInvalideException("« preuves » doit être une liste");
        }
        List<Map<String, Object>> brutes = l.stream().map(e -> e instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of()).toList();
        Milieux.Coordonnees coord = milieu.latitude() == null ? null : new Milieux.Coordonnees(milieu.latitude(), milieu.longitude(), milieu.rayonMetres());
        List<Milieux.Preuve> preuves = Milieux.preuvesValides(brutes, coord);
        depot.remplacerPreuves(ctx.tenantId(), id, preuves, () -> sequence.jetonBase64Url(OCTETS_SECRET));
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("milieu.preuves", "milieu", id, milieu.uniteCode(), milieu.preuves(), preuves));
        return lecture.lire(ctx.tenantId(), id);
    }
}

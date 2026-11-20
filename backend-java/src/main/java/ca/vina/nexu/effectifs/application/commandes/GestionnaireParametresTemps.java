package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.domaine.ChampsFeuille;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.SeuilsConvention;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;

import java.util.List;
import java.util.Map;

/** Les seuils de l'organisme (heures supplémentaires, paliers, tolérance) — jumeau de {@code modifierParametres}. */
public class GestionnaireParametresTemps {

    private final LectureFeuillesTemps lecture;
    private final EcritureFeuillesTempsPort depot;
    private final JournalEffectifsPort journal;

    public GestionnaireParametresTemps(LectureFeuillesTemps lecture, EcritureFeuillesTempsPort depot, JournalEffectifsPort journal) {
        this.lecture = lecture;
        this.depot = depot;
        this.journal = journal;
    }

    public TempsEffectifs.Parametres modifier(Contexte ctx, Map<String, Object> entree) {
        TempsEffectifs.Parametres avant = lecture.parametres(ctx.tenantId());
        TempsEffectifs.Parametres p = TempsEffectifs.parametresDe(entree, avant);
        depot.enregistrerParametres(ctx, p);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("temps.parametres", "parametres", ctx.tenantId(), null, avant, p));
        return p;
    }

    /**
     * Les seuils d'heures supplémentaires d'une convention (addenda 7, Q15) ; des seuils vides retirent la surcharge. Rend
     * la liste à jour des conventions qui ont leurs propres seuils.
     */
    public List<SeuilsConvention> modifierSeuilsConvention(Contexte ctx, String conventionCode, Map<String, Object> entree) {
        lecture.exigerConvention(ctx, conventionCode);
        SeuilsConvention avant = lecture.seuilsConventions(ctx.tenantId()).stream().filter(s -> s.conventionCode().equals(conventionCode)).findFirst()
                .orElse(null);
        SeuilsConvention s = SeuilsConvention.de(conventionCode, entree);
        depot.enregistrerSeuilsConvention(ctx, s);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("temps.seuils.convention", "convention", conventionCode, null, avant, s.estVide() ? null : s));
        return lecture.seuilsConventions(ctx.tenantId());
    }

    /** Les champs de la feuille propres à l'organisme, dans leur ordre (ETUDE-FEUILLE-DE-TEMPS-DYNAMIQUE.md). */
    public List<ChampsFeuille.ChampFeuille> champs(Contexte ctx) {
        return lecture.depot().champs(ctx.tenantId());
    }

    /** Définir un champ : un code neuf s'ajoute à la fin ; un code connu est refusé (on désactive, on ne réécrit pas). */
    public ChampsFeuille.ChampFeuille definirChamp(Contexte ctx, Map<String, Object> entree) {
        List<ChampsFeuille.ChampFeuille> existants = champs(ctx);
        ChampsFeuille.ChampFeuille c = ChampsFeuille.definir(entree, existants.size());
        if (existants.stream().anyMatch(x -> x.code().equals(c.code()))) {
            throw new ConflitException("le champ « " + c.code() + " » existe déjà : désactivez-le plutôt que de le redéfinir");
        }
        depot.enregistrerChamp(ctx.tenantId(), c);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("temps.champ.defini", "champ", c.code(), null, null, c));
        return c;
    }

    /** Activer ou désactiver : les valeurs déjà saisies restent lisibles, le champ ne se propose plus. */
    public ChampsFeuille.ChampFeuille activerChamp(Contexte ctx, String code, Map<String, Object> entree) {
        if (!(entree.get("actif") instanceof Boolean actif)) {
            throw new EffectifsInvalideException("« actif » : vrai ou faux");
        }
        ChampsFeuille.ChampFeuille avant = champs(ctx).stream().filter(x -> x.code().equals(code)).findFirst()
                .orElseThrow(() -> new IntrouvableException("champ « " + code + " » introuvable"));
        ChampsFeuille.ChampFeuille c = avant.avecActif(actif);
        depot.enregistrerChamp(ctx.tenantId(), c);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("temps.champ.active", "champ", code, null, avant, c));
        return c;
    }
}

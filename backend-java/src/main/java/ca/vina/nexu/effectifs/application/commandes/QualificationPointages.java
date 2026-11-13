package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureProfilsPointage;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.ProfilsPointage;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;

import java.util.List;
import java.util.Objects;

/**
 * <strong>Ce que le profil et le catalogue de l'organisme admettent</strong> d'un pointage ou d'une saisie après coup — la part
 * de {@code pointer} et {@code saisirAPosteriori} ({@code presence/service.ts}) qui confronte la déclaration au profil de la
 * personne (E-GP-1, E-GP-2, P-GP-1) et au référentiel des activités (E-GP-15).
 */
public class QualificationPointages {

    private static final int DATE = 10;

    private final LectureProfilsPointage profils;
    private final LectureReferentiels referentiels;

    public QualificationPointages(LectureProfilsPointage profils, LectureReferentiels referentiels) {
        this.profils = profils;
        this.referentiels = referentiels;
    }

    private static void exigerAdmise(ProfilsPointage.VerdictSaisie v) {
        if (!v.admise()) {
            throw new ConflitException(String.join(" ; ", v.motifs()));
        }
    }

    /** Un profil à déclaration après coup refuse le pointage en temps réel : sans ce refus, le profil ne serait qu'une étiquette. */
    public void exigerTempsReel(Contexte ctx, Ressource r, String date) {
        exigerAdmise(ProfilsPointage.admetSaisie(profils.deLaRessource(ctx.tenantId(), r, date), true, 0));
    }

    /**
     * La saisie après coup : le mode et la <b>fenêtre</b> du profil (sans plafond, une déclaration arriverait après la paie),
     * la nature de chaque pause, et chaque activité au catalogue (un code libre ferait des relevés une liste de fautes de frappe).
     */
    public void exigerAPosteriori(Contexte ctx, Ressource r, List<PresenceEffectifs.SegmentSaisi> segments) {
        String debut = segments.getFirst().debut();
        ProfilsPointage.ProfilPointage profil = profils.deLaRessource(ctx.tenantId(), r, debut);
        exigerAdmise(ProfilsPointage.admetSaisie(profil, false, ProfilsPointage.joursEcoules(debut, ctx.maintenant())));
        segments.stream().map(PresenceEffectifs.SegmentSaisi::nature).filter(Objects::nonNull)
                .forEach(n -> exigerAdmise(ProfilsPointage.admetNature(profil, n)));
        segments.stream().map(PresenceEffectifs.SegmentSaisi::activiteCode).filter(Objects::nonNull)
                .forEach(a -> referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.ACTIVITES_PRESENCE, a, debut.substring(0, DATE)));
    }

    /**
     * L'activité que la personne <b>commence</b> (E-GP-15) : elle n'a de sens qu'à l'ouverture d'un temps de travail. La poser
     * sur un début de pause ou une sortie donnerait des relevés par activité que personne ne peut expliquer.
     */
    public String activiteDuPointage(Contexte ctx, Object brute, PresenceEffectifs.Sens sens, String date) {
        String code = brute == null ? "" : String.valueOf(brute).strip();
        if (code.isEmpty()) {
            return null;
        }
        if (sens != PresenceEffectifs.Sens.ENTREE && sens != PresenceEffectifs.Sens.FIN_PAUSE) {
            throw new EffectifsInvalideException("une activité se déclare en commençant à travailler (entrée ou reprise après pause), pas en s'arrêtant");
        }
        return referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.ACTIVITES_PRESENCE, code, date).code();
    }
}

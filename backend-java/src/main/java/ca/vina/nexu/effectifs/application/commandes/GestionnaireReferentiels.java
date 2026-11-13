package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotReferentielsPort;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.effectifs.domaine.ValeurReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les écritures des référentiels — créer, remplacer par une version datée, fermer. Chaque écriture est
 * journalisée dans la même transaction (celle du contrôleur) : une action sans trace n'a pas eu lieu.
 */
public class GestionnaireReferentiels {

    private final DepotReferentielsPort depot;
    private final LectureReferentiels lecture;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public GestionnaireReferentiels(DepotReferentielsPort depot, LectureReferentiels lecture, JournalEffectifsPort journal, Sequence sequence) {
        this.depot = depot;
        this.lecture = lecture;
        this.journal = journal;
        this.sequence = sequence;
    }

    private void verifier(Contexte ctx, TypeReferentiel type, Referentiels.Saisie s, String date) {
        for (Referentiels.ReferenceAVerifier r : s.references()) {
            lecture.exigerValeur(ctx.tenantId(), r.type(), r.code(), date);
        }
        if (type == TypeReferentiel.UNITES) {
            Object parent = s.valeur().attributs().get("parent");
            Perimetres.verifierParent(lecture.unitesEnVigueur(ctx.tenantId(), date), s.valeur().code(), parent instanceof String p ? p : null);
        }
    }

    private ValeurReferentiel inserer(Contexte ctx, TypeReferentiel type, Referentiels.ValeurSaisie v, String depuis) {
        ValeurReferentiel r = new ValeurReferentiel(sequence.suivant("eref"), type.code(), v.code(), v.libelleFr(), v.libelleEn(), v.comportement(),
                v.ordre(), v.attributs(), depuis, null);
        depot.inserer(ctx.tenantId(), r, ctx.maintenant(), ctx.acteurId());
        return r;
    }

    /** Crée une valeur. La date de début peut être passée : c'est la reprise d'un historique. */
    public ValeurReferentiel creer(Contexte ctx, String typeCode, Map<String, Object> entree) {
        TypeReferentiel type = TypeReferentiel.exiger(typeCode);
        Referentiels.Saisie s = Referentiels.validerSaisie(type, entree);
        Object brut = entree.get("effectifDepuis");
        String depuis = brut == null || "".equals(brut) ? ctx.aujourdhui() : Referentiels.dateValide(brut, "effectifDepuis");
        if (!depot.versions(ctx.tenantId(), type, s.valeur().code()).isEmpty()) {
            throw new ConflitException(type.libelle() + " : « " + s.valeur().code() + " » existe déjà");
        }
        verifier(ctx, type, s, depuis);
        ValeurReferentiel v = inserer(ctx, type, s.valeur(), depuis);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("referentiel.cree", "referentiel." + type.code(), v.code(), null, null, v));
        return v;
    }

    /** Remplace une valeur à partir d'une date (jamais passée) : la courante se ferme, l'autre s'ouvre. */
    public ValeurReferentiel remplacer(Contexte ctx, String typeCode, String code, Map<String, Object> entree) {
        TypeReferentiel type = TypeReferentiel.exiger(typeCode);
        List<ValeurReferentiel> existantes = depot.versions(ctx.tenantId(), type, code);
        if (existantes.isEmpty()) {
            throw new IntrouvableException(type.libelle() + " : « " + code + " » introuvable");
        }
        String depuis = Referentiels.dateValide(entree.get("depuis"), "depuis");
        String fermerId = Referentiels.planifierRemplacement(existantes, depuis, ctx.aujourdhui());
        Map<String, Object> avecCode = new HashMap<>(entree);
        avecCode.put("code", code);
        Referentiels.Saisie s = Referentiels.validerSaisie(type, avecCode);
        verifier(ctx, type, s, depuis);
        ValeurReferentiel avant = existantes.stream().filter(v -> v.id().equals(fermerId)).findFirst().orElseThrow();
        depot.fermer(ctx.tenantId(), fermerId, depuis);
        ValeurReferentiel v = inserer(ctx, type, s.valeur(), depuis);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("referentiel.remplace", "referentiel." + type.code(), code, null, avant, v));
        return v;
    }

    /** Ferme une valeur à une date : elle cesse d'être proposée, les faits antérieurs la gardent. */
    public ValeurReferentiel fermer(Contexte ctx, String typeCode, String code, Map<String, Object> entree) {
        TypeReferentiel type = TypeReferentiel.exiger(typeCode);
        List<ValeurReferentiel> existantes = depot.versions(ctx.tenantId(), type, code);
        if (existantes.isEmpty()) {
            throw new IntrouvableException(type.libelle() + " : « " + code + " » introuvable");
        }
        String date = Referentiels.dateValide(entree.get("date"), "date");
        String fermerId = Referentiels.planifierFermeture(existantes, date, ctx.aujourdhui());
        ValeurReferentiel o = existantes.stream().filter(v -> v.id().equals(fermerId)).findFirst().orElseThrow();
        ValeurReferentiel fermee = new ValeurReferentiel(o.id(), o.type(), o.code(), o.libelleFr(), o.libelleEn(), o.comportement(), o.ordre(), o.attributs(), o.effectifDepuis(), date);
        depot.fermer(ctx.tenantId(), fermerId, date);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("referentiel.ferme", "referentiel." + type.code(), code, null, null, fermee));
        return fermee;
    }
}

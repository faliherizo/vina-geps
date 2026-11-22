package ca.vina.nexu.effectifs.domaine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * <strong>Une offre de quart</strong> (AD-07) : la place libérée par une absence, offerte dans un ordre figé à
 * l'ouverture. L'ordre ne bouge plus ensuite — une personne qui y entre ou en sort en cours de route ne fait pas
 * repasser les autres : c'est l'ordre qui a été annoncé, et l'encadrement le relit tel quel.
 *
 * @param urgente le quart commençait dans le seuil d'urgence : offert à tous les rangs à la fois, sans délai de dix minutes
 */
public record OffreQuart(String id, String comblementId, String absenceId, Quart quart, boolean urgente, OffresQuart.EtatOffre etat,
                         String creeLe, String attribueeA, List<Rang> rangs) {

    /** Le quart offert, lu avec la place. */
    public record Quart(String placeId, String uniteCode, String date, int debut, int fin) { }

    /** Une personne dans l'ordre de l'offre. */
    public record Rang(int rang, String ressourceId, String nom, double anciennete, double heuresComptees, double heuresOffertes,
                       OffresQuart.EtatRang etat, String offertLe, String echeance, String reponduLe, String motif) {

        public boolean estOffert() {
            return etat == OffresQuart.EtatRang.OFFERT;
        }

        /** Échu à cet instant ? (échéance exclue : à l'instant de l'échéance, c'est terminé) */
        public boolean estEchu(String maintenant) {
            return estOffert() && !Instant.parse(maintenant).isBefore(Instant.parse(echeance));
        }
    }

    public OffreQuart {
        rangs = List.copyOf(rangs);
    }

    public boolean estOuverte() {
        return etat == OffresQuart.EtatOffre.OUVERTE;
    }

    /** Les propositions dont l'échéance est passée, dans l'ordre. */
    public List<Rang> echus(String maintenant) {
        return rangs.stream().filter(r -> r.estEchu(maintenant)).toList();
    }

    public boolean aUnRangOffert() {
        return rangs.stream().anyMatch(Rang::estOffert);
    }

    /** Le premier rang qui attend encore son tour. */
    public Optional<Rang> suivant() {
        return rangs.stream().filter(r -> r.etat() == OffresQuart.EtatRang.EN_ATTENTE).findFirst();
    }

    /**
     * Qui aviser quand aucune proposition n'est en cours : le rang suivant ; à une offre urgente, tous les rangs à la
     * fois, une seule fois — après, il n'y a plus personne à qui l'offrir.
     */
    public List<Rang> aAviser() {
        if (!urgente) {
            return suivant().stream().toList();
        }
        boolean aucunePropositionFaite = rangs.stream().allMatch(r -> r.etat() == OffresQuart.EtatRang.EN_ATTENTE);
        return aucunePropositionFaite ? rangs : List.of();
    }

    /**
     * L'offre vue par une personne de l'ordre : son rang seulement. Le rang, les heures et l'ancienneté des collègues
     * relèvent de l'encadrement, pas de l'employé à qui le quart est offert.
     */
    public OffreQuart vuePour(String ressourceId) {
        return new OffreQuart(id, comblementId, absenceId, quart, urgente, etat, creeLe, attribueeA,
                rangs.stream().filter(r -> r.ressourceId().equals(ressourceId)).toList());
    }

    /**
     * Le rang de cette personne, s'il lui est offert maintenant. Quelqu'un hors de l'ordre ne voit pas l'offre (404) ;
     * dans l'ordre, la raison de son état est dite (409) : pas encore son tour, déjà répondu, échu, écarté, clos. Une
     * offre fermée n'a plus de rang offert ni en attente : sa fermeture les clôt.
     */
    public Rang exigerOffertA(String ressourceId) {
        Rang r = rangs.stream().filter(x -> x.ressourceId().equals(ressourceId)).findFirst()
                .orElseThrow(() -> new IntrouvableException("offre de quart introuvable : " + id));
        if (!r.estOffert()) {
            throw new ConflitException(r.etat().pourquoiPasOffert());
        }
        return r;
    }
}

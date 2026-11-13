package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/**
 * Un quart au sens du métier : une plage, un milieu, une composition de places. Le nombre de places pourvues
 * et le statut opérationnel se <strong>calculent</strong> ici, jamais ne se stockent.
 */
public record Equipage(String id, String periodeId, String uniteCode, String date, int debut, int fin, String natureCode, String plageCode,
                       String milieuId, Planification.ModeAttribution modeAttribution, int minimumOperationnel, String note, List<Place> places) {

    /** Une place : pourvue si elle a une ressource, vacante sinon. */
    public record Place(String id, String equipageId, int rang, String corpsEmploiCode, String posteId, String ressourceId, String ressourceNom,
                        OrigineAttribution attribueePar, String justificationForcage, String attribueeLe) { }

    /** Qui a pourvu la place : le titulaire du poste à la génération, une personne, ou le moteur. */
    public enum OrigineAttribution { POSTE, UTILISATEUR, MOTEUR }

    public int pourvues() {
        return (int) places.stream().filter(p -> p.ressourceId() != null).count();
    }

    public Planification.StatutOperationnel statut() {
        return Planification.statutOperationnel(pourvues(), minimumOperationnel);
    }

    public Planification.Creneau creneau() {
        return new Planification.Creneau(date, debut, fin);
    }
}

package ca.vina.nexu.effectifs.domaine;

/**
 * Un remplacement long terme, avec ce que la lecture y joint : le code du poste, le nom de la personne retenue et
 * le nombre d'intérêts en cours — compté à la lecture, jamais tenu à côté. Non comblé : {@code transfereLe} date le
 * passage de ses quarts à la gestion régulière, {@code quartsTransferes} les compte, {@code motifNonComble} dit pourquoi
 * quand c'est une décision de l'encadrement.
 */
public record AgphRemplacement(String id, String uniteCode, String posteId, Agph.OrigineRemplacement origine, String referenceId, String debut, String fin,
                               String finManifestation, Agph.EtatRemplacement etat, String attribueA, int placesAttribuees, String creeLe,
                               String decideLe, String transfereLe, int quartsTransferes, String motifNonComble, String posteCode, String attribueANom,
                               int interesses) {
}

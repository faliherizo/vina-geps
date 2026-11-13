package ca.vina.nexu.soutien.domaine;

import java.util.List;

/**
 * Port : le soutien est averti de chaque billet envoyé par un établissement, et de chaque
 * réponse de son auteur (demande du 13 sept. 2026). L'avis nomme l'établissement, pas son
 * identifiant ; le domaine compose le texte, l'adaptateur le fait partir.
 */
public interface AvisSoutien {

    record Billet(String id, String sujet, String description, String niveau, String auteurNom, String echeanceLe) {
    }

    record Courriel(String to, String subject, String text) {
    }

    /** Le sujet et le texte d'un avis, en français, mot pour mot ceux du Node. */
    static Courriel composer(String destinataire, Billet billet, String etablissement, boolean ouverture, String texteReponse) {
        String niveau = billet.niveau() + " — " + Soutien.descriptionNiveau(billet.niveau());
        String subject = ouverture
                ? "[Soutien] Nouveau billet " + billet.niveau() + " — " + etablissement + " : " + billet.sujet()
                : "[Soutien] Réponse de l'auteur — " + etablissement + " : " + billet.sujet();
        String corps = ouverture ? billet.description() : (texteReponse == null ? "" : texteReponse);
        String text = String.join("\n", "Établissement : " + etablissement, "Auteur : " + billet.auteurNom(),
                "Niveau : " + niveau + " (échéance " + billet.echeanceLe() + ")", "Sujet : " + billet.sujet(), "", corps, "",
                "Billet " + billet.id() + " — à traiter dans l'espace superadmin, onglet Soutien.");
        return new Courriel(destinataire, subject, text);
    }

    /** L'avis à l'auteur : une réponse du soutien, ou la résolution — avec le numéro du billet. */
    static Courriel composerPourAuteur(String destinataire, Billet billet, Integer numero, boolean reponse, String texte) {
        String num = numero == null ? "" : "#" + numero + " ";
        String subject = reponse ? "[Soutien] Réponse à votre billet " + num + ": " + billet.sujet() : "[Soutien] Votre billet " + num + "est résolu : " + billet.sujet();
        List<String> corps = reponse
                ? List.of(texte == null ? "" : texte, "", "Vous pouvez répondre depuis l'écran Soutien de l'application.")
                : List.of("Le soutien considère votre demande réglée.", "", "Si ce n'est pas le cas, rouvrez le billet depuis l'écran Soutien ; sinon, fermez-le et dites-nous, en une note de 1 à 5, comment cela s'est passé.");
        List<String> lignes = new java.util.ArrayList<>(List.of("Bonjour " + billet.auteurNom() + ",", "", "Billet " + num + "— " + billet.sujet(), ""));
        lignes.addAll(corps);
        lignes.add("");
        lignes.add("Merci.");
        return new Courriel(destinataire, subject, String.join("\n", lignes));
    }

    /** Fait partir les avis ; rend le nombre de destinataires servis. */
    int aviser(List<Courriel> courriels);

    /** Le transport en vigueur, pour que l'écran du soutien dise si un avis part vraiment. */
    default ca.vina.nexu.partage.courrier.Courrier.Transport transport() {
        return new ca.vina.nexu.partage.courrier.Courrier.Transport("journal", null, ca.vina.nexu.partage.courrier.Courrier.EXPEDITEUR_PAR_DEFAUT);
    }
}

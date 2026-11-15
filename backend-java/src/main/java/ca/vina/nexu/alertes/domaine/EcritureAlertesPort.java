package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.Remise;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;

/** L'écriture des alertes. Une réponse ou un modèle s'écrit en entier (création ou remplacement). */
public interface EcritureAlertesPort {

    void enregistrer(String tenantId, ReponsePredefinie reponse);

    /**
     * Le modèle courant, et sa version s'il en a une nouvelle : une version déjà rangée ne se réécrit jamais (une
     * activation ne crée pas de version).
     */
    void enregistrer(String tenantId, Modele modele, String instant, String auteur);

    void enregistrer(String tenantId, Alertes.Reglages reglages);

    /** L'alerte, ses réponses copiées et ses remises, en une fois. */
    void creer(Alerte alerte);

    void mettreAJour(String tenantId, String alerteId, Remise remise);

    /** Les personnes qu'une escalade ajoute à l'alerte : elles la lisent et y répondent comme les premières. */
    void ajouterRemises(String tenantId, String alerteId, java.util.List<Remise> remises);
}

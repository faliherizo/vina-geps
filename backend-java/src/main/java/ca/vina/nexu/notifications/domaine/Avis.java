package ca.vina.nexu.notifications.domaine;

import ca.vina.nexu.notifications.domaine.Evenements.Evenement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * **La forme des avis** (ADR 0029) — port de la partie pure de {@code notifications/avis.ts} et
 * {@code routesAvis.ts} : le pied de courriel (qui envoie, pourquoi, où régler — LCAP, Loi 25), le
 * texto borné (identité devant, retrait derrière), et le refus nommé d'un envoi manuel.
 */
public final class Avis {

    private Avis() {
    }

    /** Le pied de courriel : qui envoie, pourquoi, et où régler ou retirer. */
    public static String piedDeCourriel(String etablissement, Evenement e, String base, String url) {
        return "\n\nOuvrir : " + base + url + "\n—\nNexu VINA pour " + etablissement + ". Vous recevez cet avis parce que votre compte est abonné à « " + e.libelle() + " »."
                + "\nRéglez vos notifications : Mon compte › Mes notifications.";
    }

    /** Le texto : identité devant, réglage derrière, et jamais plus long que ce que le porteur accepte. */
    public static String texteDuTexto(String etablissement, String corps) {
        String fin = " Réglages : Mon compte › Mes notifications. Répondre STOP/ARRET pour ne plus recevoir de textos.";
        String tete = etablissement + " : ";
        int place = Textos.LONGUEUR_MAX - tete.length() - fin.length();
        String contenu = corps.length() > place ? corps.substring(0, place - 1) + "…" : corps;
        return tete + contenu + fin;
    }

    /** Le refus d'un envoi manuel, nommé champ par champ ({@code POST /api/notifications/envoyer}). */
    @SuppressWarnings("unchecked")
    public static List<String> refusEnvoiDirect(Map<String, Object> corps) {
        Map<String, Object> b = corps == null ? Map.of() : corps;
        List<String> erreurs = new ArrayList<>();
        List<Object> ids = b.get("employeeIds") instanceof List<?> l ? (List<Object>) l : null;
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> !(id instanceof String s) || s.isBlank())) {
            erreurs.add("employeeIds : au moins un identifiant d'employé");
        }
        if (!(b.get("sujet") instanceof String sujet) || sujet.isBlank()) {
            erreurs.add("sujet requis");
        } else if (sujet.trim().length() > 200) {
            erreurs.add("sujet : 200 caractères au plus");
        }
        if (!(b.get("corps") instanceof String texte) || texte.isBlank()) {
            erreurs.add("corps requis");
        } else if (texte.trim().length() > 2000) {
            erreurs.add("corps : 2 000 caractères au plus");
        }
        if (b.containsKey("canaux")) {
            if (!(b.get("canaux") instanceof List<?> canaux) || canaux.isEmpty()) {
                erreurs.add("canaux : au moins un canal");
            } else if (canaux.stream().anyMatch(c -> !(c instanceof String s) || Canal.de(s).isEmpty())) {
                erreurs.add("canaux : " + String.join(", ", Canal.CODES) + " attendus");
            }
        }
        return erreurs;
    }
}

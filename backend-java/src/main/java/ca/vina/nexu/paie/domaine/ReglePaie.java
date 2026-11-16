package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Une règle de paie variable de l'organisme (ADR 0084) : un effet ({@link TypeRegle} et sa valeur), des conditions, une
 * fenêtre de validité. Le code est celui du gain sur la fiche de paie ; plusieurs règles peuvent le partager, une par
 * échelon ou par période de validité.
 *
 * @param valideAu nulle : sans fin
 */
public record ReglePaie(String id, String code, String libelle, TypeRegle type, BigDecimal valeur, ConditionsRegle conditions,
                        LocalDate valideDu, LocalDate valideAu, boolean actif) {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{2,20}");
    private static final int LIBELLE_MINIMUM = 2;
    private static final int LIBELLE_MAXIMUM = 80;

    public ReglePaie {
        if (code == null || !CODE.matcher(code).matches()) {
            throw RefusPaie.invalide("« code » : 2 à 20 caractères parmi A-Z, 0-9 et _ (par exemple PRIME_NUIT)");
        }
        libelle = libelle == null ? "" : libelle.strip();
        if (libelle.length() < LIBELLE_MINIMUM || libelle.length() > LIBELLE_MAXIMUM) {
            throw RefusPaie.invalide("« libelle » : de " + LIBELLE_MINIMUM + " à " + LIBELLE_MAXIMUM + " caractères");
        }
        type.exigerValeur(valeur);
        exigerCoherence(type, conditions, valideDu, valideAu);
    }

    private static void exigerCoherence(TypeRegle type, ConditionsRegle conditions, LocalDate du, LocalDate au) {
        if (!type.estUnePrime() && conditions.visentLesHeures()) {
            throw RefusPaie.invalide("une règle « " + type.code() + " » ne dépend pas des heures : ni plage, ni jours, ni férié, ni code de paie");
        }
        if (du == null) {
            throw RefusPaie.invalide("« valideDu » est obligatoire");
        }
        if (au != null && au.isBefore(du)) {
            throw RefusPaie.invalide("« valideAu » ne précède pas « valideDu »");
        }
    }

    public ReglePaie avecId(String nouvelId) {
        return new ReglePaie(nouvelId, code, libelle, type, valeur, conditions, valideDu, valideAu, actif);
    }

    public boolean enVigueurLe(LocalDate jour) {
        return actif && !jour.isBefore(valideDu) && (valideAu == null || !jour.isAfter(valideAu));
    }

    public boolean vise(ProfilPaie profil) {
        return conditions.visent(profil);
    }

    /** Ce jour compte-t-il pour cette prime : en vigueur, et du bon jour de semaine, férié s'il le faut ? */
    boolean admetLeJour(LocalDate jour, Set<LocalDate> feries) {
        return enVigueurLe(jour) && conditions.admettent(jour, feries);
    }
}

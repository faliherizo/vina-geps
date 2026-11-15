package ca.vina.nexu.acces;

import ca.vina.nexu.acces.domaine.DecisionAcces;
import ca.vina.nexu.acces.domaine.PolitiqueAcces;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôle d'accès — équivalence avec le backend Node.
 *
 * <p>Les décomptes sont relevés sur le Node, pas recalculés : une capacité de plus ou de moins,
 * c'est un droit accordé ou refusé à tort, et cela ne se voit qu'en production.
 *
 * <p>C'est la couche que tout le reste attend : chaque contrôleur migré s'y adossera.
 */
@DisplayName("Contrôle d'accès — équivalence avec le backend Node")
class DecisionAccesTest {

    private final RegistrePolitiqueAcces registre = new RegistrePolitiqueAcces();
    private final DecisionAcces decision = registre.decision();
    private final PolitiqueAcces politique = registre.politique();

    private static final Map<String, Boolean> SANS_SURCHARGE = Map.of();

    @Test
    @DisplayName("les 37 capacités sont chargées, libellé et portée compris")
    void tableComplete() {
        assertThat(politique.capacites()).hasSize(37);
        PolitiqueAcces.Regle r = politique.regle("employee.create");
        assertThat(r.roles()).containsExactly("admin", "rh", "manager");
        assertThat(r.libelle()).isEqualTo("Créer un employé");
        // La portée borne la capacité sans figurer dans la liste des rôles : la perdre au portage
        // donnerait à un gestionnaire un périmètre qu'il n'a pas.
        assertThat(r.portee()).contains("son propre périmètre d'équipe");
    }

    @Test
    @DisplayName("chaque rôle intégré retrouve exactement son décompte de capacités")
    void decomptesParRole() {
        assertThat(decision.capacitesDe("admin", SANS_SURCHARGE)).hasSize(37);
        assertThat(decision.capacitesDe("manager", SANS_SURCHARGE)).hasSize(27);
        assertThat(decision.capacitesDe("rh", SANS_SURCHARGE)).hasSize(12);
        assertThat(decision.capacitesDe("director", SANS_SURCHARGE)).hasSize(8);
        // Aucun droit par défaut : l'employé n'agit que sur ses propres données, par des routes en
        // libre-service qui ne passent pas par cette table.
        assertThat(decision.capacitesDe("employee", SANS_SURCHARGE)).isEmpty();
        // Le comptable tient les fiches clients, leur risque et leur recouvrement — jamais le confidentiel.
        assertThat(decision.capacitesDe("accountant", SANS_SURCHARGE))
                .containsExactly("clients.manage", "clients.risque", "clients.recouvrement");
    }

    @Test
    @DisplayName("le directeur décide des approbations, tient le budget, les effectifs, les rapports d'horaire et envoie des alertes, critiques comprises, rien d'autre")
    void directeur() {
        // La gestion des effectifs (AO 26-015-DO) : le directeur d'opérations planifie, dans son périmètre d'unités.
        assertThat(decision.capacitesDe("director", SANS_SURCHARGE))
                .containsExactly("approval.decide", "budget.line.manage", "budget.transfer", "accounting.govern", "effectifs.manage",
                        "horaire.rapporter", "alertes.envoyer", "alertes.critique");
        // Le virement de budget est réservé : l'accorder au gestionnaire viderait le contrôle.
        assertThat(decision.peut("manager", "budget.transfer", SANS_SURCHARGE)).isFalse();
        assertThat(decision.peut("director", "budget.transfer", SANS_SURCHARGE)).isTrue();
    }

    @Test
    @DisplayName("le superadmin passe partout, sans consulter la table")
    void superadminPasseTout() {
        // Il n'a pas d'établissement : il n'y a pas de table de droits où le chercher.
        for (String c : politique.capacites()) {
            assertThat(decision.peut("superadmin", c, SANS_SURCHARGE)).as(c).isTrue();
        }
    }

    @Test
    @DisplayName("un rôle absent ou vide ne peut rien")
    void roleAbsent() {
        assertThat(decision.peut(null, "employee.create", SANS_SURCHARGE)).isFalse();
        assertThat(decision.peut("", "employee.create", SANS_SURCHARGE)).isFalse();
    }

    @Test
    @DisplayName("un rôle personnalisé part de zéro")
    void rolePersonnaliseSansHeritage() {
        // Hériter d'un rôle voisin accorderait des droits que personne n'a demandés.
        assertThat(decision.capacitesDe("coordonnateur-paie", SANS_SURCHARGE)).isEmpty();

        Map<String, Boolean> accorde = Map.of("varpay.pay.manage", true);
        assertThat(decision.capacitesDe("coordonnateur-paie", accorde))
                .containsExactly("varpay.pay.manage");
    }

    @Test
    @DisplayName("une surcharge d'établissement retire une capacité à un rôle intégré")
    void surchargeRetire() {
        Map<String, Boolean> retire = Map.of("payroll.run", false);
        assertThat(decision.peut("manager", "payroll.run", SANS_SURCHARGE)).isTrue();
        assertThat(decision.peut("manager", "payroll.run", retire)).isFalse();
    }

    @Test
    @DisplayName("l'administrateur garde ses droits, quoi qu'on ait enregistré")
    void administrateurInalienable() {
        // Sans cette garde, un établissement pourrait se verrouiller hors de sa propre
        // administration — et plus personne ne pourrait rouvrir.
        Map<String, Boolean> toutRetire = new LinkedHashMap<>();
        for (String c : politique.capacites()) {
            toutRetire.put(c, false);
        }
        assertThat(decision.capacitesDe("admin", toutRetire)).hasSize(37);
        assertThat(decision.peut("admin", "org.manage", toutRetire)).isTrue();

        // Le gestionnaire, lui, perd bien tout : la garde ne vaut que pour l'administrateur.
        assertThat(decision.capacitesDe("manager", toutRetire)).isEmpty();
    }

    @Test
    @DisplayName("une capacité disparue du code est ignorée, pas ressuscitée")
    void capaciteInconnueIgnoree() {
        // Une surcharge enregistrée hier ne doit pas rendre un droit que le produit ne connaît
        // plus — sinon la suppression d'une capacité laisserait une porte ouverte.
        Map<String, Boolean> fantome = Map.of("capacite.disparue", true);
        assertThat(decision.capacitesDe("rh", fantome)).hasSize(12);
        assertThat(decision.peut("rh", "capacite.disparue", fantome)).isFalse();
    }

    @Test
    @DisplayName("les capacités sortent dans l'ordre de la politique, pas dans celui des surcharges")
    void ordreStable() {
        // C'est l'ordre qu'affiche l'écran des rôles : le rendre dépendant de l'ordre
        // d'enregistrement ferait bouger la liste sans raison visible.
        Map<String, Boolean> desordre = new LinkedHashMap<>();
        desordre.put("org.manage", true);
        desordre.put("employee.create", true);

        assertThat(decision.capacitesDe("coordo", desordre))
                .containsExactly("employee.create", "org.manage");
    }


    @Test
    @DisplayName("aucune surcharge enregistrée équivaut à une table vide")
    void surchargesAbsentes() {
        // L'établissement n'a jamais ouvert l'écran des droits : la colonne est nulle en base,
        // pas vide. Les deux doivent rendre exactement les défauts du rôle.
        assertThat(decision.capacitesDe("manager", null))
                .isEqualTo(decision.capacitesDe("manager", SANS_SURCHARGE));
    }
}

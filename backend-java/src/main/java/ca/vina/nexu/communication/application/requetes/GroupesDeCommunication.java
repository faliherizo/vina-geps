package ca.vina.nexu.communication.application.requetes;

import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.GroupesTransverses;
import ca.vina.nexu.communication.domaine.GroupesTransversesPort;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * <strong>Les groupes où l'on communique</strong> : les unités, et les groupes transverses actifs (ST-G09). C'est ce que
 * voient la messagerie, les listes de distribution et les alertes. L'acheminement des appels par unité, lui, garde les
 * unités seules : une garde transverse n'est pas une unité où l'on route.
 */
public final class GroupesDeCommunication implements GroupesDeTravailPort {

    /** Le jour d'appartenance se lit à Montréal, pas en UTC : une garde finit à minuit ici. */
    private static final ZoneId MONTREAL = ZoneId.of("America/Toronto");

    private final GroupesDeTravailPort unites;
    private final GroupesTransversesPort.Lecture transverses;
    private final Clock horloge;

    public GroupesDeCommunication(GroupesDeTravailPort unites, GroupesTransversesPort.Lecture transverses, Clock horloge) {
        this.unites = unites;
        this.transverses = transverses;
        this.horloge = horloge;
    }

    private String aujourdhui() {
        return LocalDate.now(horloge.withZone(MONTREAL)).toString();
    }

    @Override
    public List<Groupe> groupes(String tenantId) {
        Stream<Groupe> gt = transverses.groupes(tenantId).stream().filter(GroupesTransverses.Groupe::actif).map(g -> new Groupe(g.id(), g.id(), g.nom()));
        return Stream.concat(unites.groupes(tenantId).stream(), gt).toList();
    }

    @Override
    public List<Membre> membres(String tenantId, String groupeId) {
        if (!GroupesTransverses.estTransverse(groupeId)) {
            return unites.membres(tenantId, groupeId);
        }
        String jour = aujourdhui();
        return transverses.groupe(tenantId, groupeId).filter(GroupesTransverses.Groupe::actif).map(g -> transverses.appartenances(tenantId, groupeId).stream()
                .filter(a -> a.active(jour)).map(a -> new Membre(a.personId(), a.nom())).toList()).orElse(List.of());
    }

    @Override
    public List<String> groupesDe(String tenantId, String personId) {
        List<String> l = new ArrayList<>(unites.groupesDe(tenantId, personId));
        String jour = aujourdhui();
        transverses.groupes(tenantId).stream().filter(GroupesTransverses.Groupe::actif)
                .filter(g -> transverses.appartenances(tenantId, g.id()).stream().anyMatch(a -> a.personId().equals(personId) && a.active(jour)))
                .forEach(g -> l.add(g.id()));
        return l;
    }
}

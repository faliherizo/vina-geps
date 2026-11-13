/**
 * **La communication du personnel, servie dans Urgences-santé.**
 *
 * L'énoncé 26-015-DO demande que le personnel se joigne : écrire au fil d'une conversation,
 * appeler un collègue, adresser une diffusion à un service. Cette fonction existait déjà,
 * construite pour les horaires cliniques (Santé Québec) — et c'est **la même** : un paramédic et
 * une infirmière se joignent de la même façon, et deux messageries dans un produit sont deux
 * boîtes où chercher un message.
 *
 * Elle garde donc **un seul foyer** (`santequebec/pages/sqmessages.tsx`, `sqlistesdiffusion.tsx`,
 * `santequebec/appels.tsx`) et gagne un second **hôte** : ces pages. L'ADR 0034 dit qu'une
 * fonction a un lieu ; il ne dit pas qu'elle n'a qu'une porte.
 *
 * Deux choses changent d'un hôte à l'autre, et deux seulement :
 *
 * - **le nom du module au bandeau** — « Urgences-santé », sans quoi on croit avoir changé
 *   d'application en changeant de page ;
 * - **la mise en page** : les horaires cliniques rangent quatre familles de sections en onglets ;
 *   ici chaque famille est une page à son adresse (« un menu, une page »), onglets retirés.
 *
 * Le périmètre (le service, la période) est **fourni ici** : les noms des participants viennent
 * des membres du groupe choisi. Sans groupe, l'identifiant reste le repli — c'est le comportement
 * du foyer, et le dupliquer autrement l'aurait fait diverger.
 */
import { FournisseurPerimetre } from "../santequebec/perimetre";
import { PageDecrite, sansOnglets, type DescripteurPage } from "../santequebec/noyau";
import { SECTIONS_APPELS, SECTIONS_CONVERSATIONS, SECTIONS_RECHERCHE } from "../santequebec/pages/sqmessages";
import { SECTIONS_LISTES } from "../santequebec/pages/sqlistesdiffusion";
import { SECTION_ACHEMINEMENT } from "./acheminementUnites";

/** Une page de communication d'Urgences-santé : le descripteur, dans le périmètre du module. */
function pageCommunication(d: DescripteurPage) {
  const Page = () => (
    <FournisseurPerimetre>
      <PageDecrite d={{ ...d, module: "Urgences-santé" }} />
    </FournisseurPerimetre>
  );
  Page.displayName = `PageUS(${d.cle})`;
  return Page;
}

export const Usmessages = pageCommunication({
  cle: "usmessages",
  titre: "Messagerie du personnel",
  intro: "Les conversations auxquelles vous participez : écrire au fil, répondre à l'émetteur "
    + "seulement, ajouter ou retirer un participant, archiver. Une réponse privée dit qui la verra "
    + "avant l'envoi. La recherche ne sort jamais de ce que votre participation vous donne à lire. "
    + "L'acheminement des avis se règle ici, unité par unité.",
  // Le service choisi donne les **noms** des participants : sans lui, le fil porterait des
  // identifiants, et c'est en recopiant un identifiant qu'on écrit à la mauvaise personne.
  perimetre: ["groupe"],
  sections: [...sansOnglets([...SECTIONS_CONVERSATIONS, ...SECTIONS_RECHERCHE]), SECTION_ACHEMINEMENT],
});

export const Usappels = pageCommunication({
  cle: "usappels",
  titre: "Appeler un collègue",
  intro: "L'appel sonne chez la personne, qui décroche ou refuse. La bannière d'appel apparaît sur "
    + "toutes les pages : on n'a pas à rester sur cet écran pour être joignable.",
  perimetre: ["groupe"],
  sections: sansOnglets(SECTIONS_APPELS),
});

export const Usdiffusion = pageCommunication({
  cle: "usdiffusion",
  titre: "Listes de diffusion du personnel",
  intro: "Les listes que vous pouvez employer : les vôtres, et celles que d'autres ont partagées. "
    + "Un membre « groupe » se déplie à chaque envoi, de sorte qu'une arrivée dans le service soit "
    + "jointe sans que personne ait touché à la liste.",
  perimetre: ["groupe"],
  sections: sansOnglets(SECTIONS_LISTES),
});

/**
 * **Les décisions de l'avis 26-015-DO, jouées par l'API — et jugées sur ce qu'elles rendent.**
 *
 * Les essais d'écran prouvent qu'une commande existe, qu'elle est atteignable et qu'on peut y
 * écrire. Aucun ne prouve qu'une règle **décide juste** : un bouton présent n'est pas une règle
 * appliquée. Celui-ci joue `donnees/urgences-sante/comportements.json` contre le backend qui sert la
 * campagne — le Java, par `backend-java/scripts/campagne-e2e.sh` — et compare chaque réponse à un
 * attendu qui porte sur une **valeur** : un statut, un motif, un taux.
 *
 * L'interprète est dans `helpers/comportements.ts` : toute la connaissance métier est dans le fichier de
 * données. Prouver une exigence de plus, c'est ajouter un scénario, pas du code.
 */
import { test } from "@playwright/test";
import { declarerComportements, type JeuComportements } from "./helpers/comportements";
import { modulesDeLOrganisme } from "./helpers/urgences-sante";
import jeu from "../../donnees/urgences-sante/comportements.json";

test.use({ storageState: { cookies: [], origins: [] } });

declarerComportements("26-015-DO — les décisions des règles, jugées par l'API", jeu as unknown as JeuComportements,
  { prefixe: "US-COMP", modules: modulesDeLOrganisme });

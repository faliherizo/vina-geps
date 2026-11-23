/**
 * **26-015-DO — les exigences technologiques qu'un comportement logiciel prouve, jouées par l'API contre le Java.**
 *
 * Profils distincts, OpenAPI 3, refus en français, charte graphique, règles d'alerte par courriel, exports, plusieurs
 * annuaires, Entra ID, fonctionnalités activées et restriction par pays. Données : `donnees/urgences-sante/technologie.json` ;
 * interprète commun : `helpers/comportements.ts`.
 */
import { test } from "@playwright/test";
import { declarerComportements, type JeuComportements } from "./helpers/comportements";
import { modulesDeLOrganisme } from "./helpers/urgences-sante";
import jeu from "../../donnees/urgences-sante/technologie.json";

test.use({ storageState: { cookies: [], origins: [] } });

declarerComportements("26-015-DO — exigences technologiques, jugées par l'API", jeu as unknown as JeuComportements,
  { prefixe: "US-TECH", modules: [...modulesDeLOrganisme, "alertes"] });

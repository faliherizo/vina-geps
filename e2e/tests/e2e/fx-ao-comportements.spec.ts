/**
 * **FX-AO-20260810 — les critères de la grille, joués par l'API contre le Java, et jugés sur des valeurs.**
 *
 * Réponses et modèles, droits, envoi à un groupe ou à des personnes, prise en charge, suivi partagé et exporté,
 * copie par courriel, cascades et reprise, notes, utilisateurs actifs, mots de passe, LDAPS, désactivation et
 * journal. Toute la connaissance est dans `donnees/fx-ao/comportements.json` ; l'interprète est commun aux
 * deux avis (`helpers/comportements.ts`).
 */
import { test } from "@playwright/test";
import { declarerComportements, type JeuComportements } from "./helpers/comportements";
import { modulesDeLOrganisme } from "./helpers/urgences-sante";
import jeu from "../../donnees/fx-ao/comportements.json";

test.use({ storageState: { cookies: [], origins: [] } });

declarerComportements("FX-AO-20260810 — les critères de la grille, jugés par l'API", jeu as unknown as JeuComportements,
  { prefixe: "FXAO-COMP", modules: [...modulesDeLOrganisme, "alertes"] });

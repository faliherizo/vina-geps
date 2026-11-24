/**
 * **L'icône « Messages non lus », sur les organismes déployés** — un vrai message en attente, cliqué dans la barre du
 * haut, ouvre la messagerie sur son fil. Le défaut (page blanche) ne paraissait qu'avec un message en attente : l'essai
 * s'assure donc d'en avoir un, sans en ajouter à chaque passage. Vise une pile semée (`E2E_BASE_URL`, `E2E_COMPTES_DEMO=1`).
 */
import { test, expect, request } from "@playwright/test";
import jeu from "../../donnees/messages-non-lus.json";
import { DEPLOYE, SEMES, allerA, apiDe, hoteDe, organismeDemo, seConnecter } from "./helpers/demonstration-deployee";

interface FilNonLu { conversationId: string; sujet: string; nonLus: number }

test.use({ storageState: { cookies: [], origins: [] } });
test.describe.configure({ timeout: 180_000 });

test.describe("Déploiement — un message non lu mène à son fil", () => {
  test.skip(!DEPLOYE || !SEMES, "vise une démonstration déployée et semée : poser E2E_BASE_URL et E2E_COMPTES_DEMO=1");

  for (const cas of jeu.cas) {
    test(`${cas.organisme} : ${cas.destinataire} clique un message non lu`, async ({ page }) => {
      const anonyme = await request.newContext();
      const hote = await hoteDe(anonyme, organismeDemo(cas.organisme));
      await anonyme.dispose();
      const lui = await apiDe(hote, cas.destinataire);
      const enAttente = async () => ((await (await lui.get(`${hote}/api/communication/conversations/non-lus`)).json()).conversations as FilNonLu[])
        .filter((f) => f.nonLus > 0);
      if ((await enAttente()).length === 0) {
        const de = await apiDe(hote, cas.expediteur);
        const direct = await de.post(`${hote}/api/communication/conversations/directe`, { data: { personId: cas.destinataire } });
        expect(direct.ok(), await direct.text()).toBeTruthy();
        const envoi = await de.post(`${hote}/api/communication/conversations/${(await direct.json()).id}/messages`, { data: { corps: jeu.texte } });
        expect(envoi.ok(), await envoi.text()).toBeTruthy();
        await de.dispose();
      }
      const [attendu] = await enAttente();
      expect(attendu, "un message en attente").toBeTruthy();
      await lui.dispose();

      const erreurs: string[] = [];
      page.on("pageerror", (e) => erreurs.push(e.message));
      await seConnecter(page, hote, cas.destinataire);
      for (const depart of ["account", "usmessages"]) {
        await allerA(page, `${hote}/admin/${depart}`);
        await page.getByTestId("avis-messages-bouton").click();
        await page.getByTestId(`avis-message-${attendu.conversationId}`).click();
        await expect(page.locator("main h1"), `depuis ${depart}`).toHaveText("Messagerie du personnel", { timeout: 30_000 });
        await expect(page.getByTestId("sq-msg-fil")).toHaveValue(attendu.conversationId, { timeout: 30_000 });
        await expect(page.getByTestId("sq-msg-entete-fil")).toBeVisible();
      }
      expect(erreurs).toEqual([]);
    });
  }
});

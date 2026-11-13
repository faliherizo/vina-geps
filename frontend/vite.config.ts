import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Le port du backend est une donnée d'environnement (`API_PORT`) : l'e2e peut viser un backend
// éphémère (E2E_API_PORT) pendant qu'une pile Docker occupe :4000 — sans ce réglage, la campagne
// parlait au mauvais serveur, et l'écran testait du code déjà déployé plutôt que celui du dépôt.
const API = `http://localhost:${process.env.API_PORT ?? process.env.E2E_API_PORT ?? "4000"}`;

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      /**
       * Les clés sont des **expressions régulières ancrées sur le segment**, pas des préfixes.
       *
       * `"/api"` mettait en correspondance tout chemin *commençant* par `/api` — donc la page
       * `/apidocs` de l'application, qui partait au backend et revenait en « Cannot GET
       * /apidocs ». L'écran « API et documentation » était ainsi injoignable par son adresse en
       * développement, et aucune campagne ne pouvait le mesurer. En production, nginx écrit
       * `location /api/` (avec la barre) et n'avait pas ce défaut : c'était un écart entre les
       * deux serveurs, le genre qui ne se voit qu'en rechargeant une page précise.
       */
      "^/api(/|$)": API,
      // Routeurs **publics** (vue des gardes, page carrière) : servis hors authentification,
      // mais servis par le backend tout de même — sans ce proxy, le serveur de développement
      // répondait la page de l'application, et `r.json()` échouait sans rien dire.
      "^/public(/|$)": API,
    },
  },
});

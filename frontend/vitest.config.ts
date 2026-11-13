/// <reference types="vitest" />
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "jsdom",
    include: ["tests/**/*.test.ts", "tests/**/*.test.tsx"],
    globals: true,
    /*
      **La couverture se mesure sur le code rendu, pas sur l'outillage.** Les essais, les
      déclarations de types et le point d'entrée n'ont rien à prouver : les compter ferait monter
      le chiffre sans ajouter une seule détection — et c'est précisément ce qu'une porte de
      qualité doit refuser.
    */
    coverage: {
      provider: "v8",
      reporter: ["text", "json-summary"],
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/main.tsx", "src/**/*.d.ts", "src/vite-env.d.ts"],
    },
  },
});

import type { CapacitorConfig } from "@capacitor/cli";

/**
 * Configuration Capacitor pour transformer le front-end React/Vite
 * en application mobile native Android et iOS.
 *
 * Pré-requis :
 *   1. cd frontend && npm install
 *   2. npm run build
 *   3. npx cap add android   (puis npx cap add ios sur macOS)
 *   4. npm run mobile:android | mobile:ios
 *
 * Variables d'environnement :
 *   - VITE_API_URL : URL absolue du backend (https://api.example.ca)
 *     car les requêtes /api/* seront servies depuis le device.
 */
const config: CapacitorConfig = {
  appId: "ca.example.freelance",
  appName: "Freelance Suite",
  webDir: "dist",
  bundledWebRuntime: false,
  server: {
    androidScheme: "https",
    cleartext: false,
  },
  android: {
    allowMixedContent: false,
  },
  ios: {
    contentInset: "always",
  },
};

export default config;

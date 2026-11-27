# Pointe des pointages — 2026-11-27 05:24

Serveur : http://localhost:4020. Chaque personne a son compte et sa place sur un quart en cours ; connexions faites avant la mesure.
Objectif E-TN-17 : p95 ≤ 2000 ms ; accord : erreurs ≤ 0.5 %. Toute réponse autre que 201 compte comme erreur.

| Vague | N | Codes | p50 (ms) | p95 (ms) | p99 (ms) | max (ms) | Verdict |
|---|---:|---|---:|---:|---:|---:|---|
| 500 entrées sur 60 s (la pointe de l'accord) | 500 | 201 × 500 | 8 | 14 | 28 | 64 | ✅ |
| 500 sorties, 50 simultanées (au-delà de la pointe) | 500 | 201 × 500 | 229 | 309 | 343 | 404 | ✅ |

## Verdict : ✅ objectifs tenus

La machine de mesure est celle du développement, partagée : un temps mesuré ici est un plafond, pas une référence de production.

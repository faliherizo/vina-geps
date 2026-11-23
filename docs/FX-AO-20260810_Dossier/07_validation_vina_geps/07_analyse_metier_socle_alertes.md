# Communications cliniques : analyse métier du socle (phase R1)

> Écrit pour l'équipe de réalisation et pour qui démontre le module. Ce document décrit le socle
> implanté le 14 novembre 2026 : les acteurs, le vocabulaire, les règles, les cas d'utilisation, et
> ce qui est volontairement laissé aux phases suivantes. Décision d'architecture : ADR 0074.

## 1. Le besoin, en une phrase

Une personne autorisée (coordonnatrice, chef d'équipe, médecin responsable) doit **joindre vite
les bonnes personnes**, savoir **qui a lu**, et savoir **qui prend en charge**. Elle le fait sans
dépendre d'une chaîne de courriels ou d'appels un à un.

## 2. Acteurs

| Acteur | Rôle VINA GePS | Ce qu'il fait dans le socle |
|---|---|---|
| Administration de l'organisme | `admin` (capacité `alertes.configure`) | Tient les **réponses prédéfinies** et les **modèles d'alerte** |
| Expéditeur autorisé | `admin`, `director`, `manager` (capacité `alertes.envoyer`) | Envoie une alerte, suit les remises, lit le suivi de toutes les alertes de l'organisme |
| Destinataire | tout compte de l'organisme | Reçoit l'alerte (application, courriel, texto, poussée), la lit, répond d'un geste |
| Exploitant | `superadmin` | Active le module `alertes` pour un organisme |

## 3. Vocabulaire (le glossaire fait foi)

| Terme | Définition |
|---|---|
| **Alerte** | Communication ciblée et priorisée, envoyée une fois, à un ensemble de destinataires figé au moment de l'envoi |
| **Priorité** | `INFORMATION`, `URGENTE` ou `CRITIQUE`. Elle décide des canaux : une alerte critique part sur **tous** les canaux, quelles que soient les préférences (critère 22 : les règles obligatoires prévalent) |
| **Réponse prédéfinie** | Geste proposé au destinataire (« J'arrive », « Indisponible »). Sa **sémantique** est fermée : `PRISE_EN_CHARGE`, `REFUS`, `INFORMATION` |
| **Modèle** | Titre, texte, priorité et réponses proposées, préparés par l'administration et repris à l'envoi |
| **Remise** | Ce qu'une alerte est pour un destinataire : quand elle lui a été envoyée, lue, et quelle réponse il a donnée |
| **Prise en charge** | Une réponse de sémantique `PRISE_EN_CHARGE`. La première prise en charge est **le fait** que l'expéditeur attend |
| **Snapshot des destinataires** | La liste des personnes résolue à l'envoi (groupes compris), conservée telle quelle. Si le groupe change ensuite, l'alerte dit quand même qui elle visait (ADR-003 du dossier) |

## 4. Règles métier du socle

| # | Règle | Critère |
|---|---|---|
| R1 | Une alerte a un titre (1 à 120 caractères), un texte (1 à 2 000), une priorité et au moins un destinataire résolu | 1, 5 |
| R2 | Les destinataires sont des personnes et des groupes, résolus en personnes **à l'envoi**. Une personne présente deux fois n'est comptée qu'une fois. L'expéditeur n'est jamais destinataire de sa propre alerte | 5, 28 |
| R3 | Les réponses proposées sont choisies parmi les réponses prédéfinies **actives** ; leur libellé et leur sémantique sont copiés dans l'alerte | 10 |
| R4 | La lecture est enregistrée la première fois seulement (idempotente) | 9 |
| R5 | Un destinataire répond une seule fois, par une réponse proposée par **cette** alerte ; répondre vaut lecture | 9, 10 |
| R6 | Seul un destinataire de l'alerte la lit ou y répond ; pour quiconque d'autre, l'alerte est **introuvable** (404, jamais 403) | 24, 28 |
| R7 | Les expéditeurs autorisés voient toutes les alertes de l'organisme et leurs remises : le suivi est **partagé** | 11, 12, 31 |
| R8 | La priorité `CRITIQUE` force tous les canaux ; les autres suivent les préférences de la personne, dans la limite des canaux essentiels | 7, 18, 22 |
| R9 | Un modèle ou une réponse se **désactive**, jamais ne s'efface : une alerte passée garde ce qu'elle a envoyé | 32 |
| R10 | Chaque geste (envoi, lecture, réponse, modification d'un modèle) est consigné au journal, sans le texte de l'alerte | 19, 89 |

## 5. Cas d'utilisation

| Cas | Acteur | Résultat |
|---|---|---|
| CU-1 Tenir les réponses prédéfinies | Administration | Libellé et sémantique, actives ou désactivées |
| CU-2 Tenir les modèles | Administration | Titre, texte, priorité, réponses proposées |
| CU-3 Envoyer une alerte | Expéditeur | Alerte créée, destinataires figés, avis publiés par canal |
| CU-4 Lire « Mes alertes » | Destinataire | Ses alertes, les plus récentes d'abord, non lues en tête ; ouvrir vaut lecture |
| CU-5 Répondre | Destinataire | Réponse enregistrée ; une prise en charge est visible de l'expéditeur |
| CU-6 Suivre | Expéditeur | Par alerte : destinataires, lues, réponses, première prise en charge ; par destinataire : état et heures |

## 6. Hors du socle (phases suivantes, ADR 0074)

| Fonction | Phase | Pourquoi plus tard |
|---|---|---|
| Relances, cascades, reprise | R2 | Orchestration durable sur l'ordonnanceur, à concevoir avec les politiques versionnées |
| Réponse par texto (mot-clé) | R2 | Webhook entrant du fournisseur, à signer et vérifier |
| Appel automatique et DTMF | R3 | Le fournisseur de voix reste à choisir (Q-TEL) |
| Alerte critique iOS (*Critical Alerts*) | R3 | Exige une autorisation d'Apple (Q-ALC) ; d'ici là, la priorité critique force tous les canaux |
| Note sur la fiche du destinataire | R4 | Le sens de « note au dossier » est à confirmer (Q-DOS) |
| Groupes transverses (spécialité, garde) | R1-bis | Le socle cible les **unités**, qui sont les groupes existants |

# ADR 0081 — Tous les PDF en PDF/A-2b par le seul producteur ; l'historique des mots de passe au seul point d'écriture

- **Statut** : accepté, le 15 novembre 2026.
- **Décideurs** : équipe VINA.
- **Concerne** :
  - `partage/pdf/DocumentPdf.java` ; les polices `src/main/resources/polices/` (Liberation Sans et sa licence) ;
  - `scripts/verifier-pdfa.sh` et l'étape veraPDF de `.github/workflows/ci.yml` ;
  - `auth/domaine/HistoriqueMotsDePasse.java`, `auth/infrastructure/persistance/DepotComptes.java` ;
  - le changeset `0016-historique-mots-de-passe.xml`.
- **Story** : ST-G10 (FX-AO-20260810, critères 39 et 86).

## Contexte

La grille exige des exports en PDF/A (critère 39), et un historique de 10 mots de passe sur 6 mois (critère 86).

- **PDF.** Tout PDF du serveur passe par `DocumentPdf`, qui employait les polices standard Helvetica, non incorporées :
  c'est la première cause de non-conformité PDF/A. Le changer touche 78 appelants directs (risque **critique** à
  l'analyse d'impact), sans que l'API ait à changer.
- **Mots de passe.** Ils changent par quatre chemins (changement par la personne, premier mot de passe,
  réinitialisation, administration ou superadmin), mais s'écrivent en un seul endroit : `DepotComptes.modifier`.

## Options considérées

1. **PDF/A partout, dans `DocumentPdf`, à API constante** (retenue) : chaque PDF devient conforme d'un coup.
2. **PDF/A à la demande** (un paramètre d'export) : deux familles de PDF, et la moins conforme resterait la plus
   employée.
3. **Une bibliothèque de conversion après coup** : une dépendance de plus pour corriger ce qu'on peut produire juste.
4. **Pour l'historique, une vérification dans chaque contrôleur** : quatre copies de la règle, dont une, un jour,
   oubliée.

## Décision

- **Polices incorporées.** Liberation Sans (normale et grasse), métriquement compatible avec Helvetica : les mises en
  page ne bougent pas. Elle est incorporée en sous-ensemble dans chaque document. Les constantes `NORMAL` et `GRAS`
  restent des repères ; `police()` les remplace par les polices du document. Le texte passe toujours par `encodable`.
  La licence (GPL v2 avec l'exception des polices, qui couvre l'incorporation dans les documents) est livrée avec le
  fichier.
- **Conformité au moment d'écrire** (une fois, même si `octets()` est appelé deux fois) : un XMP
  `pdfaid:part=2`, `conformance=B`, le même producteur que le dictionnaire d'information, le profil sRGB du JDK en
  intention de sortie, et l'identifiant du fichier.
- **Validation.** `PdfAConformiteTest` relit le document et vérifie chaque exigence, accents extractibles compris ; il
  dépose un échantillon dans `target/pdfa/`. veraPDF (image officielle) le valide en CI, juste après `mvn verify`, par
  `scripts/verifier-pdfa.sh`. Ce script échoue s'il n'a rien à valider ou pas de Docker : il ne passe jamais à vide.
- **Historique des mots de passe.** Avant toute écriture, le mot de passe proposé est comparé (BCrypt) à l'empreinte
  courante et aux 10 dernières retirées, pour celles retirées depuis moins de 6 mois. Sinon, l'empreinte courante est
  rangée, avec l'instant de son retrait, et l'historique est élagué à 10. Aucun mot de passe en clair n'est conservé.
  La règle est pure (`HistoriqueMotsDePasse`, horloge en paramètre) ; le refus est un 400 nommé sur les quatre chemins.

## Conséquences

- **Positives** : chaque PDF du produit (suivi, relevés, rapports, liasses) est un PDF d'archive ; l'historique ne
  peut pas être contourné par un chemin oublié.
- **Négatives** : un PDF pèse environ 20 à 40 Ko de plus (sous-ensembles de polices). Un mot de passe provisoire tiré
  au hasard pourrait, en théorie, tomber dans l'historique : il serait refusé, et l'administration en tirerait un
  autre.
- **À confirmer** : la première exécution de veraPDF en CI. Ici, le lancement local du script a été refusé par
  l'environnement : la conformité est vérifiée par `PdfAConformiteTest`, pas encore par veraPDF.

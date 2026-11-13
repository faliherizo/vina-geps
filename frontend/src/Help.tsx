/**
 * Page « Guide & aide » — tutoriel HTML **sémantique** (WCAG 1.3.1 : `h1`→`h6`, `p`, `ul/ol/li`)
 * expliquant le fonctionnement de l'application aux utilisateurs. Un seul `h1` par page,
 * hiérarchie logique des titres.
 */
import { useCallback, useEffect, useMemo, useRef } from "react";
import { EnTeteEcran } from "./composants";
import { sectionsVisibles } from "./aideParEcran";
import { useModuleAllowed } from "./moduleAccess";

export default function Help({ section }: { section?: string | null }) {
  const racine = useRef<HTMLDivElement>(null);

  /**
   * **Le guide ne décrit que les modules de l'établissement.**
   *
   * Il les décrivait tous : un centre qui n'a que les horaires médicaux lisait trente sections sur
   * l'approvisionnement, la fiscalité et la paie, et cherchait ensuite au menu des entrées qui
   * n'existent pas chez lui. Ce n'est pas une question de volume — c'est que le guide affirmait
   * quelque chose de faux sur son installation.
   *
   * ⚠️ Comme le masquage des sous-menus, **ce n'est pas un contrôle d'accès** : un texte ne
   * protège rien. C'est la garde de module, au serveur, qui protège.
   */
  const ouvert = useModuleAllowed();
  const sections = useMemo(() => sectionsVisibles(ouvert), [ouvert]);
  const visible = useCallback((id: string) => sections.some((x) => x.id === id), [sections]);

  /**
   * **Atteindre la section demandée, une fois la page montée.**
   *
   * Le bouton « Aide sur cet écran » tentait le défilement dans un `setTimeout(…, 0)` posé juste
   * après la navigation : la page d'aide n'était pas encore dans le document, `getElementById`
   * rendait `null`, et rien ne se passait — on atterrissait en haut du sommaire. Ici l'effet
   * s'exécute **après le rendu**, donc la section existe ; et le focus est posé sur elle pour
   * qu'une personne au clavier ou au lecteur d'écran arrive au même endroit que les autres.
   */
  useEffect(() => {
    if (!section) return;
    // `getElementById` plutôt qu'un sélecteur : il n'exige aucun échappement, là où
    // `querySelector("#" + id)` lève sur un identifiant inattendu et impose `CSS.escape`, que
    // tous les environnements n'ont pas. On vérifie ensuite que la cible est bien dans cette
    // page, pour ne pas donner le focus à un élément homonyme vivant ailleurs.
    const el = racine.current?.ownerDocument.getElementById(section) ?? null;
    if (!el || !racine.current?.contains(el)) return;
    el.setAttribute("tabindex", "-1");
    // **Le focus d'abord, le défilement ensuite.** Le défilement est un confort ; le focus est
    // ce qui amène au bon endroit une personne au clavier ou au lecteur d'écran. Les enchaîner
    // dans l'autre sens fait dépendre l'accessibilité d'une API qui peut manquer — et dans ce
    // cas la section n'était jamais atteinte, pour personne.
    el.focus({ preventScroll: true });
    el.scrollIntoView?.({ block: "start" });
  }, [section]);

  return (
    <div className="page help-page" data-testid="help-page" ref={racine}>
      <EnTeteEcran titre="Guide d'utilisation de l'application" icone="aide"
                   description="Urgences-santé planifie les effectifs par quarts : disponibilités, comblement, absences, échanges, présence et feuilles de temps, avec la communication au personnel. Ce guide explique les notions et le déroulement des tâches courantes." />

      {/*
        Le sommaire **découle du catalogue** (`SECTIONS_AIDE`) au lieu d'être écrit à la main :
        une entrée écrite ici et une section renommée là produisaient un lien mort, que seul un
        clic permettait de découvrir. L'essai de cohérence vérifie que chaque identifiant du
        catalogue est bien rendu par cette page.
      */}
      <nav aria-label="Sommaire du guide" className="card">
        <h2>Sommaire</h2>
        <ol className="help-sommaire">
          {sections.map((s) => (
            <li key={s.id}><a href={`#${s.id}`} data-testid={`help-sommaire-${s.id}`}>{s.titre}</a></li>
          ))}
        </ol>
      </nav>

{visible("demarrage") && (
        <section id="demarrage" className="card" aria-labelledby="demarrage-h">
          <h2 id="demarrage-h">Premiers pas : l'ordre logique</h2>
          <p>Pour une première mise en place, suivez cet ordre — chaque étape prépare la suivante :</p>
          <ol>
            <li><strong>Référentiels</strong> : unités, corps d'emploi, statuts, natures de quart, types d'heures et codes d'absence.</li>
            <li><strong>Périmètres</strong> : qui gère quelle unité. Sans périmètre, une console s'ouvre sur des listes vides.</li>
            <li><strong>Ressources</strong> : les personnes planifiables, liées à leur compte.</li>
            <li><strong>Postes et périodes</strong> : publier les postes, ouvrir la collecte des disponibilités, confectionner puis publier.</li>
            <li><strong>Au quotidien</strong> : comblement, absences, échanges, pointage et feuilles de temps.</li>
          </ol>
        </section>
      )}

{visible("notions") && (
        <section id="notions" className="card" aria-labelledby="notions-h">
          <h2 id="notions-h">Notions clés</h2>
          <h3>Statuts et cycles de vie</h3>
          <ul>
            <li><strong>Période</strong> : Brouillon → Collecte ouverte → Confectionnée → Publiée → Close. Une période publiée ne redevient jamais brouillon.</li>
            <li><strong>Règle</strong> : publiée à une date d'effet ; elle ne change pas le passé.</li>
            <li><strong>Feuille de temps</strong> : préparée depuis les quarts, puis approuvée palier par palier avant le lot de paie.</li>
          </ul>
          <h3>Rôles et périmètres</h3>
          <ul>
            <li><strong>Administrateur</strong> : tout l'organisme. <strong>Gestionnaire</strong> : les unités de son périmètre. <strong>Employé</strong> : ses propres données.</li>
            <li>Le <strong>périmètre</strong> d'une personne décide de ce que ses consoles montrent ; le serveur refuse par défaut.</li>
          </ul>
        </section>
      )}

{visible("mon-espace") && (
        <section id="mon-espace" className="card" aria-labelledby="mon-espace-h">
          <h2 id="mon-espace-h">Mon espace : horaire, disponibilités, échanges, temps</h2>
          <p>Les écrans que chacun ouvre pour lui-même, sans droit particulier.</p>
          <ul>
            <li><strong>Mon compte</strong> — vos coordonnées, votre mot de passe, vos questions de sécurité et vos préférences d'avis (quels événements vous atteignent, par courriel ou par texto).</li>
            <li><strong>Ma feuille de temps, Mon pointage, Mes absences, Mes disponibilités, Mes échanges, Mon AGPH</strong> — la vue personnelle des modules d'effectifs. Ce que vous y voyez ne concerne que vous ; votre gestionnaire voit la même chose depuis « Mon équipe ».</li>
          </ul>
        </section>
      )}
{visible("organisation") && (
        <section id="organisation" className="card" aria-labelledby="organisation-h">
          <h2 id="organisation-h">Organisation : accès et rôles</h2>
          <ul>
            <li><strong>Accès</strong> — les capacités accordées à chaque rôle, modifiables par organisme. Un rôle que vous définissez vous-même reçoit ses écrans par ses capacités, pas par une liste écrite dans le logiciel.</li>
            <li><strong>Approbateurs</strong> — c'est l'encadrement réel (qui relève de qui), et non le rôle du compte, qui décide qui approuve les feuilles de temps.</li>
          </ul>
        </section>
      )}

{visible("approbations") && (
        <section id="approbations" className="card" aria-labelledby="approbations-h">
          <h2 id="approbations-h">Approbations : règles, demandes, délégation</h2>
          <p>L'écran <strong>Approbations</strong> rassemble les demandes qui attendent votre décision, les règles qui les gouvernent et vos délégations.</p>

          <h3>Demandes et règles — qui doit approuver quoi</h3>
          <p>
            L'onglet <strong>Demandes</strong> montre ce qui attend une décision, palier par palier ;
            l'onglet <strong>Règles</strong> dit, par type de document, qui décide à chaque niveau. Les
            feuilles de temps des effectifs s'approuvent dans leur console (<strong>Feuilles de temps</strong>).
          </p>

          <h3>Déléguer ses approbations</h3>
          <p>
            Le bouton <strong>« Déléguer mes approbations »</strong>, dans l'en-tête de l'écran,
            confie vos validations à un collègue pour une période — des vacances, par exemple.
          </p>
          <ul>
            <li>Le délégataire se <strong>choisit dans une liste</strong> : tapez trois lettres de son nom. Seuls les comptes <strong>actifs de votre organisme</strong> sont proposés.</li>
            <li>Il est <strong>notifié par courriel</strong>, et sa signature est tracée « <strong>pour le compte de</strong> » : on sait toujours qui a décidé, et au nom de qui.</li>
            <li>Pendant la période, il <strong>voit et décide ce que vous décideriez</strong> — vos feuilles de temps. Hors période, il ne voit rien : l'écran se referme de lui-même.</li>
            <li>Une délégation <strong>ne se transmet pas</strong> : votre délégataire ne peut pas la confier à un troisième. Redéléguer l'autorité d'un tiers demande son propre consentement.</li>
          </ul>

          <h3>Si quelque chose semble bloqué</h3>
          <ul>
            <li><strong>Je ne vois pas l'écran Approbations</strong> : il s'ouvre à qui encadre quelqu'un ou a reçu une délégation. Si vous devriez l'avoir, c'est l'encadrement qu'il faut corriger, auprès de l'administration.</li>
            <li><strong>Je ne peux pas approuver ma propre demande</strong> : c'est la séparation des tâches, et elle ne se contourne pas. Ajoutez un approbateur, ou demandez à un collègue de trancher.</li>
          </ul>
        </section>
      )}
{visible("effectifs-planification") && (
        <section id="effectifs-planification" className="card" aria-labelledby="effectifs-h">
          <h2 id="effectifs-h">Effectifs : horaires, quarts, disponibilités</h2>
          <p>Pour les milieux où le travail s'organise par quarts plutôt que par horaire fixe. Les écrans sont décrits dans l'ordre où un organisme les met en service ; chacun n'agit que dans le <strong>périmètre</strong> de la personne connectée.</p>
          <h3>1. Préparer l'organisme (administration)</h3>
          <ol>
            <li><strong>Référentiels</strong> — unités, corps d'emploi, statuts d'emploi, natures de quart, types d'heures et codes d'absence. Tout le reste s'y rattache : commencer ici.</li>
            <li><strong>Milieux physiques</strong> — les lieux de travail, leurs coordonnées et leur rayon, puis les preuves de présence admises (code sur place, code QR, position, réseau).</li>
            <li><strong>Périmètres</strong> — qui gère quelle unité, à quel niveau (lecture, planification, gestion). Sans périmètre, une console s'ouvre sur des listes vides : c'est voulu, le serveur refuse par défaut.</li>
            <li><strong>Règles des effectifs</strong> — par contexte (disponibilités, absences, comblement, feuille de temps), avec un simulateur pour juger une situation avant de publier la règle. Une règle publiée est datée : elle ne change pas le passé.</li>
            <li><strong>Ressources</strong> — les personnes planifiables, avec leur matricule, leur statut et leur ancienneté ; une ressource se lie au compte de la personne.</li>
          </ol>
          <h3>2. Planifier</h3>
          <ul>
            <li><strong>Postes</strong> — code, cycle de quarts et paramètres ; se publient pour entrer dans la planification, et se modifient en lot (les conflits sont listés avant d'écrire).</li>
            <li><strong>Périodes</strong> — ouvrir la collecte des disponibilités avec sa date de fin, confectionner, publier, clore. Une période publiée ne redevient jamais brouillon.</li>
            <li><strong>Quarts et équipages</strong> — les places de chaque quart, le minimum opérationnel, le mode d'attribution (automatisé ou manuel) et les quarts ad hoc hors cycle. Forcer une attribution qu'une règle écarte exige une justification, conservée au journal.</li>
            <li><strong>Quotas</strong> — par période, plage ou type de quart ; une surcharge prend effet « à partir de », jamais en arrière.</li>
            <li><strong>Disponibilités de la période</strong> — les dépôts des employés et le bassin de candidats de chaque place, avec le motif de chaque exclusion.</li>
            <li><strong>Confection annuelle (AGPH)</strong> — la campagne annuelle : budget d'heures, tours de rôle, choix des postes, puis statuts posés à la clôture ; et les remplacements long terme.</li>
          </ul>
          <h3>3. Gérer au quotidien</h3>
          <ul>
            <li><strong>Comblement</strong> — le tableau du matin : taux de comblement, quarts sous le minimum, quotas manqués.</li>
            <li><strong>Absences et comblement</strong> — l'impact d'une demande sur le comblement avant de décider, la dérogation motivée d'un refus automatique, et les places libérées offertes d'elles-mêmes.</li>
            <li><strong>Demandes et échanges</strong> — chaque échange montre son admissibilité et sa contiguïté ; un refus nomme la règle.</li>
            <li><strong>Pointage et présence (console)</strong> — les pointages par site, les écarts d'emplacement, l'audit des localisations et les profils de pointage.</li>
            <li><strong>Feuilles de temps (console)</strong> — préparer les feuilles depuis les quarts, approuver palier par palier, lire les relevés (exportables en CSV, Excel, PDF ou JSON) et produire le lot de paie.</li>
          </ul>
          <h3>4. Contrôler</h3>
          <ul>
            <li><strong>Journal des décisions</strong> — chaque action horodatée avec son auteur ou le mécanisme qui l'a posée ; filtrable, exportable en CSV, Excel, PDF ou JSON.</li>
            <li><strong>Intégrations des effectifs</strong> — les échanges avec la paie, le bus d'entreprise et l'entrepôt de données, avec leur boîte d'envoi et leurs reprises.</li>
            <li><strong>Rétention des effectifs</strong> — combien de temps chaque classe de données est gardée ; la purge ne touche que ce qui a dépassé sa durée.</li>
          </ul>
          <h3>5. Ce que voit l'employé</h3>
          <ul>
            <li><strong>Mon horaire</strong>, <strong>Mes disponibilités</strong> (déposées tant que la collecte est ouverte, jugées à la saisie), <strong>Mes absences d'effectifs</strong> (acceptées d'office quand toutes les règles sont respectées), <strong>Mes échanges</strong>, <strong>Mon AGPH</strong> (son tour de rôle, ses postes admissibles), <strong>Mon pointage</strong> et <strong>Ma feuille de temps</strong> (les anomalies y sont nommées, avec ce qu'il faut corriger).</li>
          </ul>
          <p className="muted">Une <strong>indisponibilité</strong> (« je ne peux pas travailler ce soir-là ») n'est pas une <strong>absence</strong> (« je manque un temps de travail prévu ») : la première se déclare ici, la seconde dans Absences.</p>
        </section>
      )}
{visible("communication") && (
        <section id="communication" className="card" aria-labelledby="communication-h">
          <h2 id="communication-h">Communication : messages, appels, listes de diffusion</h2>
          <ul>
            <li><strong>Messages</strong> — les conversations avec une personne ou un groupe de travail (une unité). Un brouillon se garde ; une pièce jointe passe par l'antivirus avant d'être conservée.</li>
            <li><strong>Appels</strong> — joindre une personne au numéro du centre, sans exposer le sien ; un appel manqué reste dans l'historique.</li>
            <li><strong>Listes de diffusion</strong> — un message à plusieurs groupes de travail à la fois ; les membres d'une unité sont repris à l'envoi, jamais copiés à la main.</li>
          </ul>
          <p className="muted">Chaque envoi est consigné au journal de la communication, avec son auteur.</p>
        </section>
      )}

{visible("exploitation") && (
        <section id="exploitation" className="card" aria-labelledby="exploitation-h">
          <h2 id="exploitation-h">Exploitation : administration, sauvegardes, journal</h2>
          <ul>
            <li><strong>Rapports</strong> — le constructeur de relevés : une source (comblement, absences, feuilles…), ses colonnes, ses regroupements et ses filtres ; un relevé s'enregistre, se reproduit et s'exporte (CSV, Excel, PDF, JSON).</li>
            <li><strong>Administration</strong> — les listes de l'organisme, le journal des actions et les sauvegardes.</li>
            <li><strong>Superadministration</strong> — réservée à l'exploitant : organismes, modules activés par organisme, soutien, exploitation et sécurité.</li>
          </ul>
        </section>
      )}

{visible("accessibilite") && (
        <section id="accessibilite" className="card" aria-labelledby="a11y-h">
          <h2 id="a11y-h">Accessibilité &amp; navigation au clavier</h2>
          <ul>
            <li><strong>Tabulation</strong> : la touche <kbd>Tab</kbd> parcourt les éléments interactifs (champs, boutons, liens), les <strong>titres</strong>, <strong>libellés</strong> et <strong>tous les textes</strong> (paragraphes, listes, valeurs, badges…), ainsi que les <strong>zones de tableaux défilables</strong> (focus visible en jaune) ; utilisez les <strong>flèches</strong> pour faire défiler un tableau focalisé.</li>
            <li><strong>Onglets</strong> : <kbd>←</kbd>/<kbd>→</kbd> pour changer d'onglet, <kbd>Début</kbd>/<kbd>Fin</kbd> pour le premier/dernier.</li>
            <li><strong>Lecteurs d'écran</strong> : chaque tableau possède une légende (<code>caption</code>) et des en-têtes de colonnes (<code>th scope</code>) ; la langue de la page est le français.</li>
          </ul>
        </section>
      )}
{visible("notifications") && (
        <section id="notifications" className="card" aria-labelledby="notifications-h">
          <h2 id="notifications-h">Notifications sur le téléphone (application installable)</h2>
          <p>
            L'application web s'installe sur un téléphone ou une tablette comme une application ordinaire, et peut
            alors <strong>pousser</strong> des notifications : une proposition de remplacement, une annulation, une
            approbation attendue. Le texto et le courriel restent : la poussée s'<em>ajoute</em>, elle ne remplace rien.
          </p>
          <ol>
            <li><strong>Installer</strong> : dans le navigateur du téléphone, « Ajouter à l'écran d'accueil » (Safari : bouton Partager ; Chrome : menu ⋮ → Installer l'application).</li>
            <li><strong>Activer</strong> : page <strong>Notifications</strong>, carte « Recevoir les notifications sur cet appareil » — un interrupteur, et l'état écrit en toutes lettres.</li>
            <li><strong>Autoriser</strong> quand le téléphone le demande. Si c'est impossible (navigateur sans prise en charge, autorisation refusée, application non installée sur iPhone), la carte dit <em>pourquoi</em>, et ce qu'il faut faire.</li>
            <li><strong>Désactiver</strong> : le même interrupteur, à tout moment ; l'appareil est oublié côté serveur.</li>
          </ol>
          <p className="muted">Une notification ne contient jamais de renseignement sensible : elle nomme le geste attendu et ouvre l'écran ; le détail se lit une fois connecté.</p>
          <h3>Choisir ses canaux — courriel, texto, notification poussée</h3>
          <p>
            <strong>Mon compte → Mes canaux de notification</strong> : chaque personne choisit par où les sollicitations,
            attributions et annulations lui parviennent. Un canal fermé n'est plus emprunté ; au moins un canal écrit
            (courriel ou texto) reste ouvert, parce qu'un téléphone éteint ne reçoit pas de notification poussée.
          </p>
          <h3>Textos : fournisseur, quota, journal</h3>
          <ul>
            <li><strong>Notifications → Textos</strong> nomme le fournisseur en service. En <strong>rouge</strong>, c'est le simulateur : les textos sont journalisés mais ne partent pas — il faut régler <code>SMS_FOURNISSEUR=twilio</code> et les identifiants du compte du centre.</li>
            <li>Le <strong>quota du mois</strong> est dit en une phrase ; atteint, plus aucun texto ne part avant le mois suivant. La limite se règle par l'administration du système.</li>
            <li>Le <strong>journal</strong> montre chaque texto avec son statut (en attente, envoyé, livré, échoué) et l'erreur du fournisseur mot pour mot ; les numéros sont masqués. « Rafraîchir le statut » le redemande au fournisseur.</li>
            <li>Un <strong>envoi manuel</strong> (numéro au format international, message ≤ 480 caractères) est journalisé sous votre nom.</li>
          </ul>
        </section>
      )}
{visible("integrations") && (
        <section id="integrations" className="card" aria-labelledby="integrations-h">
          <h2 id="integrations-h">Intégrations : annuaires, textos, antivirus, clients d'API, webhooks</h2>
          <p>
            La page <strong>Intégrations</strong> (administrateur) montre <strong>une carte par liaison</strong> avec l'extérieur :
            l'état en toutes lettres, la dernière activité, les erreurs récentes — et jamais un secret. Quatre états :
          </p>
          <ul>
            <li><strong>Configuré</strong> — le tiers réel est déclaré (par l'organisme, ou par l'exploitant) ;</li>
            <li><strong>Simulé</strong> — rien de branché : la plateforme journalise (textos, EICAR) sans rien envoyer ;</li>
            <li><strong>Non configuré</strong> — la liaison est fermée tant qu'on ne la déclare pas ;</li>
            <li><strong>Démonstration</strong> — un <em>tiers de démonstration embarqué</em> répond, avec le vrai protocole du fournisseur et des données fictives.</li>
          </ul>
  </section>
      )}
{visible("soutien") && (
        <section id="soutien" className="card" aria-labelledby="soutien-h">
          <h2 id="soutien-h">Soutien : ouvrir un billet, joindre des pièces, suivre la réponse</h2>
          <p>
            <strong>Mon espace → Soutien</strong>. Un billet complet évite un aller-retour : le sujet, <strong>de quoi il
            s'agit</strong> (quelque chose ne fonctionne pas, une question, une demande, un accès, une donnée fausse),
            le <strong>niveau</strong> (qui fixe le délai de résolution engagé), <strong>qui est touché</strong> (moi, mon
            équipe, tout l'organisme), la description — ce que j'ai fait, ce qui s'est passé avec le message d'erreur
            exact, ce que j'attendais, depuis quand — et <strong>l'écran concerné</strong>, rempli d'office avec la page d'où
            vous venez ; le navigateur et l'appareil sont relevés sans que vous ayez à les chercher.
          </p>
          <ul>
            <li><strong>Pièces jointes</strong> : captures d'écran, journaux, exports — cinq par envoi, 10 Mo chacune (images,
              PDF, texte, CSV, Excel, Word, ZIP). Un exécutable est refusé avant l'envoi ; chaque pièce passe par l'antivirus
              avant d'être conservée. Elles se joignent à l'ouverture comme à chaque réponse, et se téléchargent sous leur nom.</li>
            <li><strong>Numéro</strong> : chaque billet en reçoit un (« #12 ») — c'est lui qu'on cite au téléphone.</li>
            <li><strong>Suivi</strong> : le soutien qui répond ou qui résout vous en avise par courriel (à l'adresse de votre
              compte, sinon de votre fiche). Résolu, le billet vous demande une <strong>note de 1 à 5</strong> et un mot ;
              vous confirmez et fermez, ou vous rouvrez si ce n'est pas réglé. « Ouverts » ne montre que les billets en cours ;
              « Tous » garde l'historique.</li>
            <li><strong>Côté soutien</strong> (superadmin → onglet Soutien) : les billets par organisme, les retards
              d'abord, le compte des billets ouverts sur l'onglet, la réponse avec pièces, la note interne, la résolution ; et
              le <strong>dispositif d'avis</strong> — qui reçoit les courriels, par quel transport. Sans serveur SMTP
              (<code>SMTP_HOST</code>, <code>SMTP_PORT</code>, <code>SMTP_USER</code>, <code>SMTP_PASS</code>,
              <code>MAIL_FROM</code>), les avis sont journalisés et l'écran le dit en rouge : rien ne part.</li>
          </ul>
        </section>
      )}
    </div>
  );
}

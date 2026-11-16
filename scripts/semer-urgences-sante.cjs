#!/usr/bin/env node
/**
 * **Un organisme de démonstration d'Urgences-santé**, semé avec le contenu des maquettes.
 *
 * Le module était livré et vide : pour le montrer, il fallait cliquer vingt écrans de
 * paramétrage avant que le premier tableau n'affiche autre chose que son état vide. Et un
 * organisme semé « au hasard » oblige l'évaluateur à traduire lui-même ce qu'il voit en ce que la
 * planche annonçait — c'est précisément le moment où l'on cesse de croire une démonstration.
 *
 * Les données viennent donc de `e2e/donnees/urgences-sante/demonstration.json`, où **chaque
 * valeur nomme sa planche**. Ce qui n'y figure pas n'est pas inventé : les quatre chiffres des
 * tuiles sont calculés par les écrans à partir des quarts semés ici.
 *
 * Usage :
 *   node scripts/semer-urgences-sante.cjs [--api http://localhost:4000] [--mdp 'Passe!Demo2026']
 *
 * Le superadmin est pris dans SUPERADMIN_USER / SUPERADMIN_PASS (défaut : superadmin/superadmin).
 * Le script est **idempotent par le nom** : relancé, il crée un nouvel organisme horodaté plutôt
 * que d'écraser l'ancien — une démonstration qu'on écrase pendant qu'on la montre est pire que
 * deux démonstrations.
 */
const donnees = require("../e2e/donnees/urgences-sante/demonstration.json");
const { clientApi, argument: arg, jour } = require("./lib/api-semis.cjs");

const API = arg("api", process.env.API_URL || "http://localhost:4000");
const MDP = arg("mdp", process.env.DEMO_PASSWORD || "Passe!Demo2026");
const SUPERADMIN = { username: process.env.SUPERADMIN_USER || "superadmin", password: process.env.SUPERADMIN_PASS || "superadmin" };

/** Les modules de l'avis : le socle, puis ses dépendances dans l'ordre de la chaîne. */
const MODULES = [
  "effectifs", "effectifs_regles", "effectifs_planification", "effectifs_disponibilites",
  "effectifs_absences", "effectifs_echanges", "effectifs_agph", "effectifs_presence",
  "effectifs_temps", "effectifs_integrations",
  /**
   * **La communication du personnel, et le socle qui la sert.**
   *
   * `effectifs_communication` ouvre les trois vues du menu et l'API qu'elles appellent
   * (`/api/communication`) ; il ne requiert que le socle du module.
   */
  "effectifs_communication",
  /** **La paie** (ADR 0076) : payée sur les seules feuilles approuvées — la démonstration en traite une semaine. */
  "paie",
];

/**
 * **Les sections du menu que cet organisme garde**, et par conséquent celles qu'il masque.
 *
 * Question posée le 2 novembre 2026 : « pourquoi Comptabilité et Paie & RH sont-ils présents ? ».
 * Elles ne portent **aucun code de module** et hébergent encore des écrans du socle — `rapports`,
 * `countrymodules`, `accountant` pour l'une, `orgchart` pour l'autre —, et une section paraît dès
 * qu'une de ses entrées est visible. Aucun écran de comptabilité ni de paie n'est ouvert : c'est
 * un défaut de rangement, pas d'abonnement (mesuré par
 * `frontend/tests/tenant-urgences-sante-menu.test.ts`).
 *
 * Le réglage prévu pour cela est le **masquage par organisme** (Superadmin › Modules et accès).
 * On masque donc par section, et non écran par écran : une liste de cent clés serait une liste
 * que personne ne relit. Le cœur reste — le tableau de bord est la porte d'entrée, « Mon espace »
 * tient le compte et sa sécurité, l'Administration tient les comptes et le journal ; les fermer
 * rendrait l'organisme inutilisable pour montrer quoi que ce soit.
 *
 * ⚠️ Le masquage **n'est pas un contrôle d'accès** : il dégarnit un menu et ferme l'URL directe.
 * La frontière de sécurité reste le module, gardé au serveur en refus par défaut.
 */
const SECTIONS_GARDEES = ["pilotage", "space", "admin", "urgencessante"];

const { appeler, connecter } = clientApi(API);

async function semer() {
  const jetonSa = await connecter(SUPERADMIN);
  const horodatage = new Date().toISOString().slice(0, 16).replace("T", " ");
  const nom = `${donnees.organisme.nom} (${horodatage})`;

  /**
   * **`/api/superadmin/tenants`, et non `/api/saas/tenants`.**
   *
   * La seconde crée l'organisme **sans compte d'administration** : on se connecte alors avec le
   * compte global, dont la session ne porte aucun établissement — les écrans s'ouvrent vides et
   * la moitié du menu disparaît, sans qu'aucune erreur ne s'affiche. C'est le défaut le plus
   * coûteux de cette séance, parce qu'il ressemble à un module incomplet.
   */
  const cree = await appeler("/api/superadmin/tenants", {
    methode: "POST", jeton: jetonSa,
    corps: {
      name: nom, type: "public_institution", country: "CA", region: "QC",
      planCode: donnees.organisme.planCode, adminUsername: "admin", adminPassword: MDP,
    },
  });
  const tenant = cree.tenant;
  // L'administration d'une démonstration ne doit pas buter sur le mur de configuration : c'est la
  // bonne politique en production, et un obstacle à l'écran qu'on vient montrer.
  if (cree.admin?.userId) {
    await appeler(`/api/auth/users/${cree.admin.userId}`, {
      methode: "PATCH", jeton: jetonSa, corps: { securitySetupRequired: false, mustChangePassword: false },
    });
  }
  await appeler(`/api/saas/tenants/${tenant.id}/modules`, {
    methode: "PUT", jeton: jetonSa, corps: { modules: MODULES },
  });

  // ── Dégarnir le menu de ce qui n'est pas d'Urgences-santé ────────────────
  /**
   * Le catalogue des vues est lu au serveur plutôt que recopié ici : une liste figée dans ce
   * script resterait muette le jour où une vue s'ajoute, et c'est exactement le genre d'écart
   * qu'on ne voit qu'à la démonstration suivante.
   */
  const catalogue = await appeler("/api/saas/vues", { jeton: jetonSa });
  const aMasquer = catalogue.vues.filter((v) => !SECTIONS_GARDEES.includes(v.section)).map((v) => v.cle);
  await appeler(`/api/saas/tenants/${tenant.id}/vues`, {
    methode: "PUT", jeton: jetonSa, corps: { masquees: aMasquer },
  });

  // L'administration de l'organisme : c'est elle qui pose le paramétrage, comme en production.
  const jeton = await connecter({ username: "admin", password: MDP, tenantId: tenant.id });
  const ctx = { jeton, tenantId: tenant.id };
  const poser = (chemin, corps) => appeler(chemin, { ...ctx, methode: "POST", corps });
  const ref = (type, corps) => poser(`/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...corps });
  const sansPlanche = ({ planche, ...reste }) => reste;   // la planche documente, elle ne se poste pas

  // ── Le référentiel : ce que les planches nomment ──────────────────────────
  await ref("unites", sansPlanche(donnees.unite));
  await ref("corps_emploi", sansPlanche(donnees.corpsEmploi));
  for (const s of donnees.statutsEmploi) await ref("statuts_emploi", sansPlanche(s));
  await ref("types_milieu", sansPlanche(donnees.typeMilieu));
  for (const p of donnees.plages) {
    const { debut, fin, planche, ...reste } = p;
    await ref("plages_horaires", { ...reste, attributs: { debut, fin } });
  }
  for (const n of donnees.naturesQuart) await ref("natures_quart", sansPlanche(n));
  for (const t of donnees.typesHeure) await ref("types_heure", sansPlanche(t));

  // ── Les sites des planches ────────────────────────────────────────────────
  const milieux = {};
  for (const m of donnees.milieux) {
    milieux[m.code] = (await poser("/api/effectifs/milieux", {
      code: m.code, libelle: m.libelle, typeCode: donnees.typeMilieu.code, uniteCode: donnees.unite.code,
    })).id;
  }

  // ── Les personnes, et leur compte ────────────────────────────────────────
  const comptes = [];
  for (const p of donnees.personnes) {
    /*
      Un compte par personne, nommé par son nom de connexion : c'est l'identité de la messagerie,
      et la ressource d'effectifs (créée plus bas) porte le nom que les écrans affichent.
    */
    const compte = await poser("/api/auth/users", {
      username: p.compte.username, password: MDP, role: p.compte.role, mustChangePassword: false,
    });
    // Le mur de configuration (second facteur, questions) est la bonne politique en production ;
    // sur une démonstration, il empêche simplement d'ouvrir l'écran qu'on vient montrer.
    await appeler(`/api/auth/users/${compte.id}`, {
      ...ctx, methode: "PATCH", corps: { securitySetupRequired: false },
    });
    // **Une personne sans matricule n'est pas planifiable** : les ressources humaines ont un
    // compte et un espace personnel, pas de quarts. Lui créer une ressource la ferait apparaître
    // dans les bassins de candidats — et c'est ainsi qu'on finit par planifier quelqu'un qui
    // n'est pas sur le terrain.
    if (p.matricule) {
      await poser("/api/effectifs/planification/ressources", {
        matricule: p.matricule, nom: p.nom, utilisateurId: compte.id,
        corpsEmploiCode: donnees.corpsEmploi.code, uniteCode: donnees.unite.code,
        statutEmploiCode: p.statutEmploiCode, anciennete: p.anciennete,
      });
    }
    // **Le périmètre d'encadrement**, et le serveur refuse par défaut : sans lui, la console d'une
    // gestionnaire s'ouvre sur des listes vides — c'est ce que la démonstration montrait jusqu'ici.
    if (p.perimetre) {
      await poser("/api/effectifs/perimetres", {
        utilisateurId: compte.id, uniteCode: donnees.unite.code, etendue: "SOUS_ARBRE", niveau: p.perimetre,
      });
    }
    comptes.push({ ...p.compte, nom: p.nom, profil: p.profil, compteId: compte.id });
  }

  // ── Les postes des planches, publiés ─────────────────────────────────────
  const cycle = [["J", "J", "J", null, null, null, null]];
  const postes = [];
  for (const p of donnees.postes) {
    postes.push((await poser("/api/effectifs/planification/postes", {
      code: p.code, libelle: p.libelle, uniteCode: donnees.unite.code,
      statutEmploiCode: p.statutEmploiCode, corpsEmploiCode: donnees.corpsEmploi.code,
      milieuId: milieux[p.milieu], natureCode: "REG", cycle, debutCycle: jour(-7),
    })).id);
  }
  await poser("/api/effectifs/planification/postes/publier", { ids: postes });
  await semerAgph(postes).catch((e) => console.warn(`  AGPH non semée : ${e.message.slice(0, 200)}`));

  /**
   * **Une campagne AGPH en cours**, pour que « Mon AGPH » ait quelque chose à montrer : l'année suivante, l'unité et le
   * corps d'emploi de la démonstration, ouverte puis démarrée — le tour de rôle suit l'ancienneté, et la première
   * personne peut choisir son poste. Un remplacement long terme est ouvert aux manifestations d'intérêt.
   */
  async function semerAgph(ids) {
    const annee = Number(jour(0).slice(0, 4)) + 1;
    const campagne = await poser("/api/effectifs/agph/campagnes",
      { uniteCode: donnees.unite.code, corpsEmploiCode: donnees.corpsEmploi.code, annee, delaiChoixMinutes: 2880 });
    await poser(`/api/effectifs/agph/campagnes/${campagne.id}/ouvrir`, {});
    await poser(`/api/effectifs/agph/campagnes/${campagne.id}/demarrer`, {});
    await poser("/api/effectifs/agph/remplacements",
      { posteId: ids[ids.length - 1], origine: "POSTE_VACANT", debut: jour(30), fin: jour(60), finManifestation: jour(14) });
  }

  // ── Une période publiée, et les trois quarts du jour (planche s01) ───────
  /**
   * **Deux périodes, et c'est le domaine qui l'impose.**
   *
   * La collecte se termine au plus tard le premier jour de la période, et jamais dans le passé :
   * une période déjà commencée ne peut donc pas en avoir eu une. La semaine courante — celle des
   * quarts des planches — passe directement en confection, puis est publiée. Une seconde période,
   * à venir, reste **en collecte** : c'est elle qui donne à « Disponibilités » quelque chose à
   * déposer (planche s02).
   */
  const periode = await poser("/api/effectifs/planification/periodes",
    { nom: "Semaine courante", uniteCode: donnees.unite.code, debut: jour(-3), fin: jour(10) });
  for (const etat of ["CONFECTION", "PUBLIEE"]) {
    await poser(`/api/effectifs/planification/periodes/${periode.id}/etat`, { etat });
  }

  const collecte = await poser("/api/effectifs/planification/periodes",
    { nom: "Prochaine période — collecte en cours", uniteCode: donnees.unite.code, debut: jour(14), fin: jour(27) });
  await poser(`/api/effectifs/planification/periodes/${collecte.id}/etat`,
    { etat: "COLLECTE", finCollecte: jour(7) });

  const ressources = await appeler("/api/effectifs/planification/ressources", ctx);
  const quarts = [];
  for (const q of donnees.quartsDuJour) {
    const plage = donnees.plages.find((p) => p.code === q.plage);
    const equipage = await poser("/api/effectifs/planification/equipages", {
      periodeId: periode.id, date: jour(0), uniteCode: donnees.unite.code,
      milieuId: milieux[q.milieu], natureCode: "ADH", debut: plage.debut, fin: plage.fin,
      minimumOperationnel: q.pourvues,
      // La composition se dit en lignes { corps d'emploi, nombre } : c'est le contrat du domaine,
      // et il porte l'intention — « trois paramédics », non « trois places anonymes ».
      composition: [{ corpsEmploiCode: donnees.corpsEmploi.code, nombre: q.places }],
    });
    // Le comblement des planches, à la place près : 3 / 4, 1 / 3, 2 / 2.
    for (let i = 0; i < q.pourvues && i < equipage.places.length && i < ressources.length; i++) {
      await poser(`/api/effectifs/planification/places/${equipage.places[i].id}/attribuer`,
        { ressourceId: ressources[i].id, forcer: true, justification: "Attribution de démonstration (maquette s01)" });
    }
    quarts.push(q.code);
  }

  // ── Les règles nommées par les planches s07 et s11 ───────────────────────
  for (const r of donnees.regles) {
    // La règle porte une **clé** (son nom stable), une portée typée et une condition : ce sont
    // les mots du domaine, et s'en écarter fait répondre 422 — ce qui est la bonne façon d'échouer.
    await poser("/api/effectifs/regles", {
      cle: r.code, contexte: r.contexte, porteeType: "UNITE", porteeCode: donnees.unite.code,
      // Une règle ne prend jamais effet dans le passé : on ne juge pas après coup des décisions
      // déjà prises. Elle entre donc en vigueur aujourd'hui.
      condition: r.condition, action: r.action, message: r.message, effectifDepuis: jour(0),
    }).catch((e) => console.warn(`  règle « ${r.libelle} » non posée : ${e.message.slice(0, 160)}`));
  }

  // ── La communication du personnel (planche P11) ──────────────────────────
  /**
   * **Un groupe, puis un fil** — dans cet ordre, et pas par goût du détail.
   *
   * Le groupe est ce qui donne les **noms** : la messagerie lit les membres du groupe choisi
   * pour remplacer les identifiants par des noms. Un fil ouvert sans groupe s'afficherait
   * correctement et illisiblement.
   *
   * Les deux messages ne sont pas du remplissage : ils montrent ce que la planche P11 promet —
   * un dernier auteur, un extrait, et un compte de non-lus pour celui qui n'a pas encore ouvert.
   */
  const com = donnees.communication;
  /*
    Le groupe de travail est l'unité d'effectifs : ses membres sont ses ressources liées à un
    compte. Les participants sont des noms de connexion — l'identité que la messagerie reconnaît.
  */
  await poser("/api/communication/conversations", {
    sujet: com.conversation.sujet, type: "groupe", groupeId: donnees.unite.code,
    participants: comptes.map((c) => c.username),
  })
    .then(async (fil) => {
      for (const corps of com.conversation.messages) {
        await poser(`/api/communication/conversations/${fil.id}/messages`, { corps });
      }
    })
    .catch((e) => console.warn(`  communication non semée : ${e.message.slice(0, 200)}`));

  // ── Les feuilles de temps de la semaine écoulée (planche s09) ────────────
  await poser("/api/effectifs/temps/feuilles/preparer",
    { uniteCode: donnees.unite.code, debut: jour(-6), fin: jour(0) });

  await semerPaie(ctx, tenant.id, comptes)
    .catch((e) => console.warn(`  paie non semée : ${e.message.slice(0, 200)}`));

  return { tenant, comptes, quarts, periode };
}

/**
 * **Une semaine payée**, pour que « Mes bulletins » montre un vrai bulletin (ADR 0076, 0084).
 *
 * La paie est d'abord **configurée** comme le ferait la RH : la validation des temps est exigée, l'échelle salariale et
 * les primes sont posées (`demonstration.json`, rubrique `paie`). Chaque personne planifiable déclare trois jours de
 * huit heures, la soumet, l'administration l'approuve ; son dossier porte son **échelon**, pas un taux saisi. La paie
 * valide les feuilles sans anomalie, prépare le cycle et le traite — les feuilles deviennent « payées ».
 */
async function semerPaie(ctx, tenantId, comptes) {
  const reglages = await appeler("/api/paie/reglages", ctx);
  await appeler("/api/paie/reglages", { ...ctx, methode: "PUT", corps: { tauxFss: String(reglages.tauxFss), tauxCnesst: String(reglages.tauxCnesst),
    majorationSupplementaire: String(reglages.majorationSupplementaire), validationTempsExigee: donnees.paie.validationTempsExigee } });
  for (const r of donnees.paie.regles) await appeler("/api/paie/regles", { ...ctx, methode: "POST", corps: r });
  const ressources = await appeler("/api/effectifs/planification/ressources", ctx);
  const payables = comptes.filter((c) => ressources.some((r) => r.utilisateurId === c.compteId));
  for (const [i, c] of payables.entries()) {
    const jeton = await connecter({ username: c.username, password: MDP, tenantId });
    const moi = { jeton, tenantId };
    const feuille = (await appeler("/api/effectifs/temps/moi", moi)).find((f) => f.debut === jour(-6)) ?? null;
    if (!feuille) throw new Error(`aucune feuille de la semaine pour ${c.username}`);
    for (const d of [-6, -5, -4]) {
      await appeler(`/api/effectifs/temps/feuilles/${feuille.id}/segments`, { ...moi, methode: "POST",
        corps: { date: jour(d), debut: `${jour(d)}T12:00:00Z`, fin: `${jour(d)}T20:00:00Z`, typeHeureCode: "REG" } });
    }
    await appeler(`/api/effectifs/temps/feuilles/${feuille.id}/soumettre`, { ...moi, methode: "POST" });
    await appeler(`/api/effectifs/temps/feuilles/${feuille.id}/approuver`, { ...ctx, methode: "POST" });
    const ressource = ressources.find((r) => r.utilisateurId === c.compteId);
    const echelon = donnees.paie.echelons[c.username];
    await appeler(`/api/paie/dossiers/${ressource.id}`, { ...ctx, methode: "PUT",
      corps: echelon ? { mode: "horaire", frequence: "hebdomadaire", echelon } : { mode: "horaire", frequence: "hebdomadaire", tauxHoraire: String(34 + i * 2.5) } });
  }
  await appeler("/api/paie/temps/valider-sans-anomalie", { ...ctx, methode: "POST", corps: { debut: jour(-6), fin: jour(0) } });
  const cycle = await appeler("/api/paie/cycles", { ...ctx, methode: "POST", corps: { debut: jour(-6), fin: jour(0), datePaie: jour(4) } });
  await appeler(`/api/paie/cycles/${cycle.cycle.id}/traiter`, { ...ctx, methode: "POST" });
}

semer().then(({ tenant, comptes, quarts }) => {
  // Une ligne **lisible par une machine** : l'essai de bout en bout sème par ce script plutôt que
  // de recopier ses appels, et il doit retrouver l'organisme sans analyser une phrase française.
  console.log(`TENANT=${tenant.id}`);
  console.log(`\n✅ Organisme « ${tenant.name} » semé — ${tenant.id}`);
  console.log(`   Sites : ${donnees.milieux.map((m) => m.libelle).join(", ")}`);
  console.log(`   Quarts du jour : ${quarts.join(", ")}`);
  console.log("\n   Comptes (choisir cet organisme à la connexion) :");
  console.log(`     admin / ${MDP}  — administration de l'organisme`);
  for (const c of comptes) console.log(`     ${c.username} / ${MDP}  — ${c.nom} — ${c.profil}`);
  console.log("");
}).catch((e) => {
  console.error(`\n❌ ${e.message}\n`);
  process.exit(1);
});

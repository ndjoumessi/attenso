import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { describe, it } from 'node:test';

/**
 * Le gestionnaire « fetch » de la coquille, éprouvé réseau coupé.
 *
 * <p>C'est la promesse la plus chargée du produit — ENF-03 : « le mode dégradé
 * est obligatoire, pas une compensation ». Jusqu'ici seul pagePour était testé,
 * c'est-à-dire la table des correspondances, pas la décision. Une table juste
 * dans un gestionnaire cassé ne sert à rien.
 *
 * <p>On charge le fichier LIVRÉ, pas le modèle : c'est lui qui s'exécute sur la
 * tablette, et le charger vérifie au passage que la construction a bien
 * substitué le nom du cache et la liste des fichiers.
 */

const LIVREE = 'src/main/resources/static/sw.js';
const ORIGINE = 'https://attenso.test';

/**
 * Le nombre de fichiers du précache, FIGÉ. 15 jusqu'à la refonte du tableau,
 * 16 avec le chunk partagé qr-*.js, extrait du tracé du QR et importé par
 * l'accueil, et 17 depuis chrome-*.js — le retour et la bande légale, qui
 * étaient recopiés sur cinq pages et dont une seule copie portait les classes
 * donnant 44 px. Les deux conditions posées par le message d'échec sont
 * vérifiées : aucune entrée Vite ajoutée, et la croissance est bien un module
 * partagé devenu chunk. Légitime ; la prochaine croissance doit se voir.
 */
// 17 → 16 le 2026-11-26 : `chrome.jsx` importe maintenant `formulaire.jsx`
// pour l'anneau de focus partagé, et Vite a FUSIONNÉ les deux chunks communs.
// La coquille a donc une requête de moins. Vérifié avant d'accepter : aucune
// entrée Vite ajoutée, et `chrome-*.js` pèse 2 837 o — négligeable contre les
// 220 Ko du runtime React qu'elle met déjà en cache.
const FICHIERS_DE_LA_COQUILLE = 16;

/** Une réponse factice, assez pour distinguer d'où elle vient. */
function reponse(provenance, chemin) {
  return { provenance, chemin };
}

/**
 * Charge la coquille dans un environnement de service worker simulé.
 *
 * @param reseau ce que fait `fetch` : une fonction, qui peut rejeter
 * @param enCache les chemins présents dans le cache
 */
function chargerLaCoquille({ reseau, enCache = [] }) {
  assert.ok(existsSync(LIVREE), `${LIVREE} absent : construire le front d'abord`);
  const code = readFileSync(LIVREE, 'utf8');

  const ecouteurs = new Map();
  const journal = { ajoutes: null, supprimes: [], ouverts: [] };
  const presents = new Set(enCache);

  const self = {
    addEventListener: (nom, gestionnaire) => ecouteurs.set(nom, gestionnaire),
    location: { origin: ORIGINE },
    skipWaiting() {},
    clients: { claim() {}, matchAll: async () => [], openWindow: async () => {} },
    registration: { showNotification: async () => {} },
  };

  const cheminDe = (cle) =>
    (typeof cle === 'string' ? new URL(cle, ORIGINE) : new URL(cle.url)).pathname;

  const caches = {
    open: async (nom) => {
      journal.ouverts.push(nom);
      return { addAll: async (liste) => { journal.ajoutes = liste; } };
    },
    keys: async () => ['attenso-coquille-perimee', nomDuCache(code)],
    delete: async (nom) => { journal.supprimes.push(nom); return true; },
    match: async (cle) => {
      const chemin = cheminDe(cle);
      return presents.has(chemin) ? reponse('cache', chemin) : undefined;
    },
  };

  new Function('self', 'caches', 'fetch', code)(self, caches, reseau);
  assert.ok(ecouteurs.has('fetch'), 'la coquille n\'écoute pas « fetch »');
  return { ecouteurs, journal };
}

function nomDuCache(code) {
  const trouve = code.match(/const CACHE = '([^']+)'/);
  assert.ok(trouve, 'nom du cache introuvable dans la coquille livrée');
  return trouve[1];
}

/** Un réseau qui tombe, comme pendant une panne. */
const RESEAU_TOMBE = async () => {
  throw new TypeError('Failed to fetch');
};

/** Un réseau debout. */
const RESEAU_DEBOUT = async (requete) => reponse('reseau', new URL(requete.url).pathname);

/** Joue une requête contre le gestionnaire et rend ce qu'il a décidé. */
async function demander(coquille, chemin, methode = 'GET', origine = ORIGINE) {
  const evenement = {
    request: { url: new URL(chemin, origine).toString(), method: methode },
    repondu: null,
  };
  evenement.respondWith = (promesse) => { evenement.repondu = promesse; };
  coquille.ecouteurs.get('fetch')(evenement);

  if (evenement.repondu === null) {
    return { intercepte: false };
  }
  try {
    return { intercepte: true, reponse: await evenement.repondu };
  } catch (panne) {
    return { intercepte: true, panne: String(panne) };
  }
}

describe('la coquille sert le tableau quand le réseau tombe', () => {
  it('laisse passer le réseau quand il est debout', async () => {
    // Le cache ne doit JAMAIS court-circuiter un serveur joignable : la file
    // affichée doit être celle du serveur, pas la dernière connue.
    //
    // Le chemin demandé est DÉLIBÉRÉMENT présent dans le cache. Une première
    // version de ce test demandait /connexion avec seulement /app/accueil.html
    // en cache : en stratégie cache-d'abord, caches.match('/connexion') ne
    // trouve rien et retombe sur le réseau, donc le test passait dans les deux
    // cas et ne prouvait rien. Il faut une entrée exacte pour que les deux
    // stratégies divergent.
    const coquille = chargerLaCoquille({
      reseau: RESEAU_DEBOUT, enCache: ['/app/accueil.html'],
    });
    const r = await demander(coquille, '/app/accueil.html');
    assert.equal(r.reponse.provenance, 'reseau',
      'le cache ne doit pas court-circuiter un serveur joignable');
  });

  it('sert la page du tableau quand le réseau tombe sur /connexion', async () => {
    // LE CAS QUI COMPTE. /connexion est l'URL du lien « Se connecter », donc
    // celle qu'une tablette met en favori. Rien n'est en cache sous ce chemin :
    // tout repose sur le repli par pagePour.
    const coquille = chargerLaCoquille({
      reseau: RESEAU_TOMBE, enCache: ['/app/accueil.html'],
    });
    const r = await demander(coquille, '/connexion');
    assert.equal(r.intercepte, true);
    assert.deepEqual(r.reponse, { provenance: 'cache', chemin: '/app/accueil.html' });
  });

  it('replie chaque route publique sur sa page', async () => {
    const coquille = chargerLaCoquille({
      reseau: RESEAU_TOMBE,
      enCache: ['/app/accueil.html', '/app/comptoir.html', '/app/suivi.html',
        '/app/ecran.html'],
    });
    const attendu = [
      ['/accueil', '/app/accueil.html'],
      ['/connexion', '/app/accueil.html'],
      ['/t/cerisiers', '/app/comptoir.html'],
      ['/s/ABC123', '/app/suivi.html'],
      ['/ecran/jeton-xyz', '/app/ecran.html'],
    ];
    for (const [demande, page] of attendu) {
      const r = await demander(coquille, demande);
      assert.equal(r.reponse?.chemin, page, `${demande} doit replier sur ${page}`);
    }
  });

  it('préfère une entrée en cache exacte au repli', async () => {
    // Les bundles et la feuille de style sont en cache sous leur propre
    // chemin : les faire passer par pagePour renverrait du HTML pour du CSS.
    const coquille = chargerLaCoquille({
      reseau: RESEAU_TOMBE, enCache: ['/app/assets/styles-abc.css'],
    });
    const r = await demander(coquille, '/app/assets/styles-abc.css');
    assert.equal(r.reponse.chemin, '/app/assets/styles-abc.css');
  });

  it('ne cache pas la panne quand elle n\'a rien à servir', async () => {
    // La vitrine n'est pas en coquille — un prospect hors réseau n'existe pas.
    // Rendre une page vide ou un succès factice serait pire que l'erreur du
    // navigateur, qui au moins dit la vérité.
    const coquille = chargerLaCoquille({ reseau: RESEAU_TOMBE, enCache: [] });
    const r = await demander(coquille, '/');
    assert.equal(r.intercepte, true);
    assert.match(r.panne, /Failed to fetch/);
  });

  it('ne touche JAMAIS aux données, réseau debout ou non', async () => {
    // « Afficher 2 personnes devant vous alors que c'est le tour du client
    // depuis dix minutes est pire que pas d'écran du tout. »
    for (const reseau of [RESEAU_DEBOUT, RESEAU_TOMBE]) {
      const coquille = chargerLaCoquille({ reseau, enCache: ['/api/file'] });
      const r = await demander(coquille, '/api/file');
      assert.equal(r.intercepte, false, '/api/ ne doit pas être interceptée');
    }
  });

  it('ne touche ni aux écritures ni aux autres origines', async () => {
    const coquille = chargerLaCoquille({
      reseau: RESEAU_TOMBE, enCache: ['/app/accueil.html'],
    });
    assert.equal((await demander(coquille, '/connexion', 'POST')).intercepte, false);
    assert.equal(
      (await demander(coquille, '/connexion', 'GET', 'https://ailleurs.test')).intercepte,
      false);
  });
});

describe('la coquille s\'installe et se nettoie', () => {
  it('met en cache toute la liste produite par la construction', async () => {
    const coquille = chargerLaCoquille({ reseau: RESEAU_DEBOUT });
    const evenement = { attentes: [] };
    evenement.waitUntil = (p) => evenement.attentes.push(p);

    coquille.ecouteurs.get('install')(evenement);
    await Promise.all(evenement.attentes);

    assert.ok(Array.isArray(coquille.journal.ajoutes), 'rien n\'a été mis en cache');
    assert.equal(coquille.journal.ajoutes.length, FICHIERS_DE_LA_COQUILLE,
      `la coquille compte ${coquille.journal.ajoutes.length} fichiers, pas `
      + `${FICHIERS_DE_LA_COQUILLE}. Le nombre a changé : vérifier d'abord qu'aucune `
      + 'entrée Vite n\'a été ajoutée contre la conception (vite.config.js, '
      + 'PAGES_HORS_RESEAU de prerender.mjs), puis, si la croissance est légitime '
      + '(un module partagé devenu chunk, comme qr-*.js), mettre FICHIERS_DE_LA_COQUILLE '
      + 'à jour en disant pourquoi.');
    assert.ok(coquille.journal.ajoutes.includes('/app/accueil.html'));
  });

  it('supprime les caches périmés et garde le sien', async () => {
    // Supprimer le cache courant à l'activation viderait la coquille à chaque
    // déploiement, et la tablette se retrouverait sans repli jusqu'au prochain
    // passage en ligne — exactement quand elle en a besoin.
    const code = readFileSync(LIVREE, 'utf8');
    const coquille = chargerLaCoquille({ reseau: RESEAU_DEBOUT });
    const evenement = { attentes: [] };
    evenement.waitUntil = (p) => evenement.attentes.push(p);

    coquille.ecouteurs.get('activate')(evenement);
    await Promise.all(evenement.attentes);

    assert.deepEqual(coquille.journal.supprimes, ['attenso-coquille-perimee']);
    assert.ok(!coquille.journal.supprimes.includes(nomDuCache(code)));
  });
});

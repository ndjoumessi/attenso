import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';

const CONTROLEUR = 'src/main/java/ai/attenso/web/ControleurPages.java';
const COQUILLE = 'front/sw.js';
const CONSTRUCTION = 'front/prerender.mjs';

/**
 * Les pages que la coquille met en cache, lues dans le script de construction
 * — la même source que celle qui écrit la liste dans le service worker.
 */
function pagesEnCache() {
  const source = readFileSync(CONSTRUCTION, 'utf8');
  const bloc = source.match(/PAGES_HORS_RESEAU\s*=\s*\[([^\]]*)\]/);
  assert.ok(bloc, 'PAGES_HORS_RESEAU introuvable dans prerender.mjs');
  const pages = bloc[1].split(',')
    .map((mot) => mot.trim().replace(/^['"]|['"]$/g, ''))
    .filter(Boolean)
    .map((page) => `/app/${page}.html`);
  assert.ok(pages.length >= 4, 'liste de la coquille suspecte');
  return new Set(pages);
}

/** Les routes de pages du serveur : chemin déclaré -> page servie. */
function routesDuServeur() {
  const source = readFileSync(CONTROLEUR, 'utf8');
  const motif = /@GetMapping\("([^"]+)"\)[\s\S]{0,300}?return "forward:([^"]+)"/g;
  const routes = [...source.matchAll(motif)].map(([, chemin, page]) => ({ chemin, page }));
  assert.ok(routes.length >= 12, 'ControleurPages semble incomplet');
  return routes;
}

/**
 * La fonction `pagePour` du service worker, extraite du modèle. Le fichier
 * n'est pas un module et porte des marqueurs remplacés à la construction : on
 * ne peut pas l'importer, mais on peut en isoler cette fonction, qui ne
 * dépend de rien.
 */
function miroirDeLaCoquille() {
  const source = readFileSync(COQUILLE, 'utf8');
  const bloc = source.match(/function pagePour\(chemin\) \{[\s\S]*?\n\}/);
  assert.ok(bloc, 'pagePour introuvable dans sw.js');
  return new Function(`${bloc[0]}\nreturn pagePour;`)();
}

/** Un chemin concret pour une route paramétrée : /s/{reference} -> /s/ABC123. */
function cheminConcret(declare) {
  return declare.replace(/\{[^}]+\}/g, 'ABC123');
}

/** Les pages hors réseau, FIGÉES : accueil, suivi, comptoir, ecran. Une cinquième
 *  veut dire qu'une entrée a été ajoutée contre la conception. */
describe('la liste des pages hors réseau est figée', () => {
  it('compte exactement quatre pages', () => {
    const pages = [...pagesEnCache()].sort();
    assert.deepEqual(pages, ['/app/accueil.html', '/app/comptoir.html',
      '/app/ecran.html', '/app/suivi.html'],
    `les pages hors réseau ont changé : ${pages}. Vérifier qu'aucune entrée Vite `
    + "n'a été ajoutée contre la conception, puis mettre cette liste à jour si c'est voulu.");
  });
});

describe('la coquille hors ligne est un miroir fidèle des routes du serveur', () => {
  it('connaît toutes les routes qui servent une page mise en cache', () => {
    // C'est la promesse du mode dégradé : si la tablette recharge pendant une
    // panne, le navigateur doit retrouver la page. Une route ajoutée au
    // contrôleur sans l'être ici laisse ce rechargement sur une erreur réseau,
    // avec les gestes de l'ASV enfermés dans une page qu'elle ne peut plus
    // ouvrir. C'est arrivé avec /connexion.
    const enCache = pagesEnCache();
    const pagePour = miroirDeLaCoquille();

    for (const { chemin, page } of routesDuServeur()) {
      if (!enCache.has(page)) continue;
      const concret = cheminConcret(chemin);
      assert.equal(pagePour(concret), page,
        `la route ${chemin} sert ${page}, que la coquille ne sait pas retrouver`);
    }
  });

  it('ne promet jamais une page absente du cache', () => {
    // Renvoyer une page non mise en cache ferait échouer caches.match et
    // retomber sur l'erreur réseau, en donnant l'illusion d'un repli.
    const enCache = pagesEnCache();
    const pagePour = miroirDeLaCoquille();

    for (const { chemin } of routesDuServeur()) {
      const page = pagePour(cheminConcret(chemin));
      if (page !== null) {
        assert.ok(enCache.has(page),
          `la coquille promet ${page} pour ${chemin}, qui n'est pas en cache`);
      }
    }
  });

  it('laisse la vitrine dehors, et le déduit du cache plutôt que de le coder', () => {
    // La vitrine n'est pas mise en cache — un prospect hors réseau n'existe
    // pas. La règle ci-dessus l'exclut donc d'elle-même : c'est volontaire, et
    // ce test le dit explicitement pour qu'on ne la rajoute pas par réflexe.
    assert.equal(pagesEnCache().has('/app/vitrine.html'), false);
    assert.equal(miroirDeLaCoquille()('/'), null);
  });
});

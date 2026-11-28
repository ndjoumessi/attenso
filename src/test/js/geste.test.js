import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { balises, balisesButton } from './aide/balises.js';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (c) => readFileSync(resolve(RACINE, c), 'utf8');
const rendu = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/\{\/\*[\s\S]*?\*\/\}/g, '').replace(/^\s*\/\/.*$/gm, '');

/** Les fichiers écrits à la main. `ui/` est du shadcn vendorisé : pas à nous. */
function sources(dossier = 'front/src', prefixe = '') {
  const out = [];
  for (const e of readdirSync(resolve(RACINE, dossier), { withFileTypes: true })) {
    if (e.isDirectory()) {
      if (e.name === 'ui') continue;
      out.push(...sources(`${dossier}/${e.name}`, `${prefixe}${e.name}/`));
    } else if (e.name.endsWith('.jsx')) {
      out.push([`${prefixe}${e.name}`, rendu(lire(`${dossier}/${e.name}`))]);
    }
  }
  return out;
}

/**
 * Une déclaration `const X = …;` et son corps entier.
 *
 * <p>Le motif d'avant exigeait que la constante se REFERME sur le délimiteur
 * qui l'ouvre. Or une constante peut ouvrir sur une chaîne et finir sur un
 * gabarit — `DECLENCHEUR` le fait depuis qu'il interpole l'anneau. Elle
 * n'était alors plus reconnue du tout, et sa garde a déclaré nu un bouton
 * qui ne l'était pas : un faux positif, mais le même motif aurait tout aussi
 * bien pu taire un vrai défaut.
 */
const CONSTANTES = /(?:export )?const ([A-Za-z_][A-Za-z0-9_]*) = ([\s\S]*?);\n/g;

/** Les constantes de classes qui portent le geste, d'où qu'elles viennent. */
function porteuses() {
  const noms = new Set(['PRESSE']);
  for (const [, s] of sources()) {
    for (const m of s.matchAll(CONSTANTES)) {
      if (m[2].includes('active:scale') || /\$\{PRESSE\}/.test(m[2])) noms.add(m[1]);
    }
  }
  return [...noms];
}

const porteLeGeste = (balise, noms) => balise.includes('active:scale')
  || noms.some((n) => new RegExp(`\\b${n}\\b`).test(balise));

/**
 * Sur la TABLETTE DU COMPTOIR, `hover:` ne se déclenche jamais : Tailwind 4
 * enveloppe lui-même chaque règle `hover:` dans `@media (hover:hover)`
 * — vérifié sur le CSS construit, 13 règles sur 13. Vingt-trois des
 * vingt-six boutons écrits à la main n'avaient QUE `hover:` pour toute
 * réaction. Autrement dit, sur l'appareil où l'ASV passe sa journée, ils ne
 * répondaient rien du tout à l'appui.
 */
describe('un bouton répond quand on appuie dessus', () => {
  it('porte le geste, partout, depuis une seule source', () => {
    const noms = porteuses();
    const muets = [];
    for (const [nom, s] of sources()) {
      for (const b of balisesButton(s)) {
        if (!porteLeGeste(b, noms)) muets.push(`${nom} : ${b.slice(0, 60).replace(/\s+/g, ' ')}`);
      }
    }
    assert.deepEqual(muets, [], 'des boutons ne réagissent pas à l\'appui');
  });

  it('tient ce geste dans une seule fonction', () => {
    const src = rendu(lire('front/src/composants/formulaire.jsx'));
    assert.equal((src.match(/^export const PRESSE = /gm) ?? []).length, 1,
      'le geste d\'appui n\'a pas de source unique');
    assert.match(src, /export const PRESSE[\s\S]{0,200}active:scale-\[0\.97\]/,
      'la source du geste ne porte pas d\'enfoncement');
  });

  /**
   * LE PIÈGE, rencontré en le posant : Tailwind 4 rend `scale-[0.97]` sur la
   * propriété CSS `scale`, PAS sur `transform`. Une balise qui déclare
   * `transition-[filter,box-shadow,transform]` et un `active:scale` n'anime
   * donc rien — l'enfoncement claque au lieu de glisser. Les quatre boutons
   * concernés étaient dans ce cas, et l'ancien `active:translate-y-px` l'était
   * déjà avant moi, pour la même raison.
   *
   * <p>Vérifié au navigateur après correction : au `pointerdown`, le bouton
   * principal fait bien tourner une transition sur `scale`, 150 ms, courbe du
   * projet. Rien dans le texte ne le disait ; cette garde le dit.
   */
  it('met « scale » dans sa liste de transition, et non « transform »', () => {
    const fautifs = [];
    for (const [nom, s] of sources()) {
      for (const b of s.match(/transition-\[[a-z,-]*\]/g) ?? []) {
        if (b.includes('transform')) fautifs.push(`${nom} : ${b}`);
      }
    }
    assert.deepEqual(fautifs, [],
      '« transform » n\'anime pas `scale` : l\'enfoncement claquerait');
  });

  /**
   * Deux utilitaires `transition` sur une même balise se départagent par
   * l'ordre de la FEUILLE, pas par celui des classes : le gagnant n'est pas
   * celui qu'on croit. `PRESSE` apporte la sienne, donc il ne va que sur un
   * élément qui n'en déclare aucune.
   */
  it('ne superpose jamais deux transitions sur la même balise', () => {
    const fautifs = [];
    for (const [nom, s] of sources()) {
      for (const b of balisesButton(s)) {
        if (/\$\{PRESSE\}/.test(b) && /transition-\[/.test(b)) fautifs.push(nom);
      }
    }
    assert.deepEqual(fautifs, [], 'deux transitions se disputent la même balise');
  });
});

/**
 * `--courbe-sortie` valait cubic-bezier(0.4, 0, 1, 1) : un ease-in, à 1,8 %
 * du chemin au dixième de sa durée. C'est la courbe qui part lentement, donc
 * celle qui fait paraître une interface molle. Elle n'était utilisée nulle
 * part — mais elle portait le nom qu'on vient chercher pour animer une sortie.
 * Un jeton mort qui est un piège finit par servir.
 */
describe('le bloc Mouvement ne déclare rien de mort', () => {
  it('n\'a aucun jeton de mouvement sans emploi', () => {
    const jetons = lire('src/main/resources/static/app/jetons.css');
    const bloc = jetons.match(/--- Mouvement [\s\S]*?\*\/([\s\S]*?)\n\n/);
    assert.ok(bloc, 'le bloc Mouvement est introuvable');
    const declares = [...bloc[1].matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)].map((m) => m[1]);
    assert.ok(declares.length > 0, 'le bloc Mouvement ne déclare plus rien');

    const tout = [jetons, lire('front/src/styles.css'),
      ...sources().map(([, s]) => s)].join('\n');
    const morts = declares.filter((n) => !tout.includes(`var(${n})`));
    assert.deepEqual(morts, [], 'des jetons de mouvement ne servent à rien');
  });
});

/**
 * L'anneau de focus était écrit à la main sur dix boutons — et ABSENT des
 * dix-sept liens, qui retombaient donc sur celui du navigateur. Deux anneaux
 * différents dans la même page selon qu'on tabule vers un bouton ou vers un
 * lien. Personne ne le voit élément par élément ; tout le monde le ressent
 * sur un parcours au clavier.
 */
describe('tout ce qui prend le focus porte le même anneau', () => {
  /** Les constantes de classes qui portent l'anneau, d'où qu'elles viennent. */
  function porteuses() {
    const noms = new Set(['ANNEAU']);
    for (const [, s] of sources()) {
      for (const m of s.matchAll(CONSTANTES)) {
        if (/\$\{ANNEAU\}|outline-\[var\(--anneau\)\]/.test(m[2])) noms.add(m[1]);
      }
    }
    return [...noms];
  }

  it('sur les liens comme sur les boutons, depuis une seule source', () => {
    const noms = porteuses();
    const nus = [];
    for (const [nom, s] of sources()) {
      for (const element of ['button', 'a']) {
        for (const b of balises(s, element)) {
          if (!b.includes('className')) continue;
          if (noms.some((n) => new RegExp(`\\b${n}\\b`).test(b))) continue;
          nus.push(`${nom} : <${element}> ${b.slice(0, 50).replace(/\s+/g, ' ')}`);
        }
      }
    }
    assert.deepEqual(nus, [], 'des éléments focalisables n\'ont pas l\'anneau du projet');
  });

  /**
   * La source dit bien l'anneau DU PROJET. Les autres assertions ne vérifient
   * qu'une référence à `ANNEAU` ; mesuré par mutation, remplacer sa couleur
   * par `outline-blue-500` les laissait toutes vertes. Une source unique qui
   * dit la mauvaise chose se propage simplement partout, d'un coup.
   */
  it('tient la couleur et l\'épaisseur du projet', () => {
    const src = rendu(lire('front/src/composants/formulaire.jsx'));
    const m = src.match(/export const ANNEAU = ([\s\S]*?);\n/);
    assert.ok(m, 'l\'anneau partagé a disparu');
    assert.match(m[1], /outline-\[var\(--anneau\)\]/,
      'l\'anneau n\'utilise pas la couleur du projet');
    assert.match(m[1], /focus-visible:outline-2/, 'l\'anneau n\'a plus d\'épaisseur');
  });

  /**
   * UNE source. Un anneau recopié diverge — et c'est exactement ce qui
   * était arrivé : dix copies du même triplet, et les liens oubliés.
   */
  it('ne réécrit l\'anneau nulle part ailleurs', () => {
    const copies = [];
    for (const [nom, s] of sources()) {
      const n = (s.match(/outline-\[var\(--anneau\)\]/g) ?? []).length;
      // Le fichier source a droit à UNE occurrence : sa définition. Exempter
      // le fichier entier, comme je l'avais fait, laissait `CHAMP` réécrire
      // l'anneau juste au-dessus d'`ANNEAU` sans que rien ne le dise — et
      // c'est une mutation qui frappait la mauvaise ligne qui me l'a appris.
      // Deux dans le fichier source depuis le 2026-11-28 : la définition, et
      // `ANNEAU_LIENS`, la variante `[&_a]:` pour les liens qu'on ne peut pas
      // habiller un par un — le HTML juridique injecté n'est pas du JSX.
      // Elle ne peut pas être composée depuis `ANNEAU` : Tailwind lit le texte
      // des sources. Le compte reste un fil-piège, et l'assertion ci-dessous
      // empêche que la deuxième place serve à autre chose qu'à cette variante.
      const permis = nom === 'composants/formulaire.jsx' ? 2 : 0;
      if (n > permis) copies.push(`${nom} (${n} fois)`);
    }
    assert.deepEqual(copies, [], 'l\'anneau est réécrit hors de sa source');
    // La deuxième occurrence du fichier source est bien la variante
    // descendante, et pas une copie de plus qui aurait pris sa place.
    const src = rendu(lire('front/src/composants/formulaire.jsx'));
    assert.match(src, /export const ANNEAU_LIENS = [\s\S]*?\[&_a\]:focus-visible:outline-\[var\(--anneau\)\]/,
      'la seconde écriture de l\'anneau n\'est pas la variante des liens');
  });
});

import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (c) => readFileSync(resolve(RACINE, c), 'utf8');
const rendu = (s) => s.replace(/\{\/\*[\s\S]*?\*\/\}/g, '').replace(/^\s*\/\/.*$/gm, '');

/** Les fichiers écrits à la main. `ui/` est du shadcn vendorisé : pas à nous. */
function sources(dossier = 'front/src', prefixe = '') {
  const out = [];
  for (const e of readdirSync(resolve(RACINE, dossier), { withFileTypes: true })) {
    if (e.isDirectory()) {
      if (e.name === 'ui') continue;
      out.push(...sources(`${dossier}/${e.name}`, `${prefixe}${e.name}/`));
    }
    else if (e.name.endsWith('.jsx')) {
      out.push([`${prefixe}${e.name}`, rendu(lire(`${dossier}/${e.name}`))]);
    }
  }
  return out;
}

/**
 * Les `.map(` d'un fichier, avec le nom de leur paramètre.
 *
 * <p>Borné par appariement de parenthèses, et c'est nécessaire : un bouton
 * UNIQUE a tout à fait le droit de porter un nom constant. La propriété gardée
 * ici ne concerne que les boutons répétés, donc ceux rendus dans une liste.
 *
 * <p>Les régions s'imbriquent — `reservation.jsx` groupe par jour puis itère
 * les heures — donc un même bouton est vu deux fois. C'est voulu : le message
 * d'échec dédoublonne, le balayage non, pour qu'un bouton compte quelle que
 * soit la boucle qui le porte.
 */
function regionsMap(source) {
  const out = [];
  for (const m of source.matchAll(/\.map\(\(?(\[[^\]]*\]|[A-Za-z_$][\w$]*)/g)) {
    let i = source.indexOf('(', m.index + '.map'.length) + 1;
    let profondeur = 1;
    for (; i < source.length && profondeur > 0; i += 1) {
      if (source[i] === '(') profondeur += 1;
      else if (source[i] === ')') profondeur -= 1;
    }
    out.push({
      parametres: m[1].replace(/[[\]\s]/g, '').split(',').filter(Boolean),
      corps: source.slice(m.index, i),
    });
  }
  return out;
}

/**
 * Le nom accessible de ce bouton désigne-t-il l'élément de la liste ?
 *
 * <p>Exigence : un `aria-label` en gabarit qui interpole le paramètre de la
 * boucle. C'est strict, et les deux règles plus souples que j'ai essayées
 * avant étaient toutes deux AVEUGLES — mesuré, pas supposé :
 *
 * <ol>
 * <li>« le bloc contient une interpolation » : satisfaite par un `${PRESSE}`
 *     dans la `className` ou un `key={route}`, qui ne nomment rien.</li>
 * <li>« le contenu visible contient une accolade » : satisfaite par
 *     `{membre.actif ? 'Désactiver' : 'Réactiver'}`, qui MENTIONNE l'élément
 *     mais ne rend que DEUX noms pour cinq membres. Retirer entièrement un
 *     `aria-label` laissait alors la garde verte.</li>
 * </ol>
 *
 * <p>Un `title` ne suffit pas : il ne devient le nom accessible qu'à défaut de
 * tout le reste, et les lecteurs d'écran ne le traitent pas uniformément. Les
 * flèches de la file en portaient un, et leur nom restait « ↑ ».
 *
 * <p><strong>CE QUE CETTE GARDE NE VOIT PAS, et il faut le savoir.</strong>
 * Elle exige que le nom interpole un paramètre de la boucle du MÊME fichier.
 * Or la répétition peut venir d'un composant parent : dans `File.jsx`, le
 * `.map` local ne parcourt que les deux flèches d'UNE ligne, et c'est `File`
 * qui répète `Ligne` par ticket. Un nom `` `${titre} un ticket` `` satisfait
 * donc la règle tout en restant constant d'un ticket à l'autre — mutation
 * essayée, restée verte.
 *
 * <p>La propriété complète — « deux éléments focalisables à l'écran ne
 * partagent pas un nom » — ne se décide pas sur le source : elle demande une
 * page rendue. Cette garde prouve une condition nécessaire, pas l'absence du
 * défaut.
 */
function nomDesigneLElement(bloc, parametres) {
  const gabarit = bloc.match(/aria-label=\{`([^`]*)`\}/);
  if (!gabarit) return false;
  return parametres.some((p) => new RegExp(`\\$\\{[^}]*\\b${p}\\b`).test(gabarit[1]));
}

/**
 * Dans une liste, deux boutons ne portent pas le même nom.
 *
 * <p>Mesuré le 2026-11-26. La liste de l'équipe rendait autant de boutons
 * « Désactiver » que de membres ; celle des carnets autant de « Montrer le QR »
 * que d'animaux ; la file autant de « ↑ » que de tickets ; et la réservation
 * un « 14:00 » par jour ouvert. Rigoureusement indiscernables dans l'arbre
 * d'accessibilité : le contexte de la ligne lève l'ambiguïté pour l'œil, jamais
 * pour qui énumère les éléments focalisables.
 *
 * <p>C'est la zone du produit où se tromper de ligne coûte le plus cher : on y
 * coupe un accès, on y change un rôle, on y révoque un QR, on y déplace le tour
 * de quelqu'un.
 *
 * <p>L'audit qui a motivé cette garde n'avait vu QUE les deux boutons de
 * l'équipe. Les deux des carnets sont apparus en écrivant la garde, les flèches
 * et les créneaux en la durcissant — quatre cas sur six trouvés par elle et non
 * par la relecture. Même histoire que l'anneau de focus, annoncé sur deux liens
 * et trouvé absent des vingt.
 *
 * <p>WCAG 2.4.6 (Headings and Labels) et 4.1.2 (Name, Role, Value).
 */
describe('deux boutons d\'une même liste ne portent pas le même nom', () => {
  it('chaque bouton rendu dans un .map nomme son élément', () => {
    const constants = new Set();
    for (const [nom, source] of sources()) {
      for (const { parametres, corps } of regionsMap(source)) {
        for (const m of corps.matchAll(/<button[\s\S]*?<\/button>/g)) {
          if (!nomDesigneLElement(m[0], parametres)) {
            constants.add(`${nom} : ${m[0].slice(0, 70).replace(/\s+/g, ' ')}`);
          }
        }
      }
    }
    assert.deepEqual([...constants], [],
      'des boutons de liste sont indiscernables au nom accessible');
  });

  /**
   * La garde a de quoi mordre. Sans ça, un `.map` renommé ou une régression du
   * balayage la rendrait verte sur un fichier qu'elle n'examine plus.
   */
  it('examine effectivement les boutons de liste du produit', () => {
    let vus = 0;
    const fichiers = new Set();
    for (const [nom, source] of sources()) {
      for (const { corps } of regionsMap(source)) {
        const n = [...corps.matchAll(/<button[\s\S]*?<\/button>/g)].length;
        if (n > 0) fichiers.add(nom);
        vus += n;
      }
    }
    assert.ok(vus >= 7, `seulement ${vus} bouton(s) de liste examiné(s)`);
    assert.ok(fichiers.size >= 4,
      `seulement ${fichiers.size} fichier(s) avec des boutons de liste`);
  });
});

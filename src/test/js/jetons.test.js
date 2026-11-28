import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const JETONS = 'src/main/resources/static/app/jetons.css';
const source = readFileSync(JETONS, 'utf8');

function declarations(bloc) {
  const valeurs = {};
  for (const [, nom, valeur] of bloc.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    valeurs[nom] = valeur;
  }
  return valeurs;
}

/**
 * Les jetons effectifs d'un mode, cascade comprise.
 *
 * <p>Le mode sombre ne redéfinit que ce qui change : les couleurs du mur, par
 * exemple, restent celles de :root parce que le mur est un écran physique
 * toujours sombre. Lire le bloc @media isolément donnerait des jetons absents
 * là où CSS, lui, hérite — c'est ce que la première version de ce test
 * faisait, et elle accusait le code à tort.
 */
function jetons(mode) {
  const coupure = source.indexOf('@media (prefers-color-scheme: dark)');
  const clair = declarations(source.slice(0, coupure));
  if (mode === 'clair') return clair;
  return { ...clair, ...declarations(source.slice(coupure)) };
}

const canal = (c) => {
  const v = c / 255;
  return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
};
const luminance = (hex) => {
  const n = hex.slice(1);
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(n.slice(i, i + 2), 16));
  return 0.2126 * canal(r) + 0.7152 * canal(g) + 0.0722 * canal(b);
};
const contraste = (a, b) => {
  const [x, y] = [luminance(a), luminance(b)];
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
};

/**
 * Les paires qui doivent tenir, dans les deux modes.
 *
 * @param seuil 4.5 pour du texte, 3 pour une bordure de contrôle ou un grand
 *        caractère (WCAG 1.4.3 et 1.4.11).
 */
const PAIRES = [
  ['texte sur fond', 'sur-fond', 'fond', 4.5],
  ['texte secondaire sur fond', 'sur-fond-doux', 'fond', 4.5],
  ['texte secondaire sur surface', 'sur-fond-doux', 'surface', 4.5],
  ['texte sur accent', 'sur-accent', 'accent', 4.5],
  ['texte sur alerte', 'sur-alerte', 'alerte', 4.5],
  ['texte sur attention', 'sur-attention', 'attention', 4.5],
  ['alerte sur fond', 'alerte', 'fond', 4.5],
  ['attention sur fond', 'attention', 'fond', 4.5],
  ['accent sur fond', 'accent', 'fond', 4.5],
  // La paire qui manquait, et qui échouait : le texte des bandeaux
  // d'information est posé sur le VOILE, pas sur le fond. En mode sombre
  // --accent sur --accent-voile ne donnait que 4.28:1, et cette classe porte
  // aussi le bandeau des gestes en attente — donc l'écran de panne, là où la
  // lecture compte le plus.
  ['texte d\'information sur voile', 'accent-texte', 'accent-voile', 4.5],
  // Une bordure de CONTRÔLE porte l'information « ici commence un champ » :
  // elle est soumise au contraste, contrairement à une séparation décorative.
  ['bordure de contrôle sur fond', 'bordure-controle', 'fond', 3],
  ['texte sur bande de rupture', 'sur-bande-contraste', 'bande-contraste', 4.5],
  ['texte doux sur bande', 'sur-bande-contraste-doux', 'bande-contraste', 4.5],
  // Le mur garde le même fond dans les deux modes : il est physique.
  ['mur : encre sur fond', 'mur-encre', 'mur-fond', 4.5],
  ['mur : encre douce sur fond', 'mur-encre-douce', 'mur-fond', 4.5],
  // L'affiche aussi : elle sort d'une imprimante. Le contraste s'y vérifie
  // comme ailleurs, mode sombre compris, puisqu'elle ne change pas.
  ['affiche : encre sur papier', 'sur-papier', 'papier', 4.5],
  ['affiche : encre douce sur papier', 'sur-papier-doux', 'papier', 4.5],
  // La barre latérale est du CHROME : elle se distingue du contenu, donc elle a
  // son propre fond, donc ses propres paires à mesurer.
  ['barre : libellé sur fond de barre', 'sur-barre', 'barre', 4.5],
  ['barre : libellé doux sur fond de barre', 'sur-barre-doux', 'barre', 4.5],
  ['barre : libellé de l\'entrée active', 'sur-barre-active', 'barre-active', 4.5],
  // La bordure de la barre sépare deux surfaces porteuses d'information : elle
  // dit « ici finit la navigation ». Soumise au 3:1 comme une bordure de contrôle.
  ['barre : bordure sur fond', 'barre-bordure', 'fond', 3],
  // La bordure touche aussi la barre : un trait qui sépare deux surfaces doit
  // se lire contre chacune, pas contre une seule.
  ['barre : bordure sur fond de barre', 'barre-bordure', 'barre', 3],
  // L'ÉTAT « page courante » : le test de libellé ne le voit pas, il mesure le
  // texte, pas la distinction. Un voile à 1.09:1 de la barre passait tout et
  // restait invisible ; WCAG 1.4.11 attend 3:1 pour un indicateur d'état.
  ['barre : l\'entrée active se distingue de la barre', 'barre-active', 'barre', 3],
];

for (const mode of ['clair', 'sombre']) {
  describe(`les jetons en mode ${mode}`, () => {
    const palette = jetons(mode);

    for (const [nom, avant, arriere, seuil] of PAIRES) {
      it(`${nom} atteint ${seuil}:1`, () => {
        const fg = palette[avant];
        const bg = palette[arriere];
        assert.ok(fg, `jeton --${avant} absent du mode ${mode}`);
        assert.ok(bg, `jeton --${arriere} absent`);
        const mesure = contraste(fg, bg);
        assert.ok(mesure >= seuil,
          `${fg} sur ${bg} donne ${mesure.toFixed(2)}:1, il faut ${seuil}:1`);
      });
    }
  });
}

describe('la bande de rupture', () => {
  it('est plus CLAIRE que le fond en mode sombre, pas plus foncée', () => {
    // Une inversion naïve la rendrait invisible : c'est le défaut que la
    // première version avait, vu seulement à l'écran.
    const sombre = jetons('sombre');
    assert.ok(luminance(sombre['bande-contraste']) > luminance(sombre.fond),
      'en mode sombre, une surface qui doit trancher est plus claire que le fond');
  });

  it('est plus foncée que le fond en mode clair', () => {
    const clair = jetons('clair');
    assert.ok(luminance(clair['bande-contraste']) < luminance(clair.fond));
  });
});

describe('l\'entrée active de la barre', () => {
  for (const mode of ['clair', 'sombre']) {
    it(`ne repose pas sur la teinte du libellé seule, en mode ${mode}`, () => {
      // Même patron que la bande de rupture : on affirme une propriété que le
      // ratio ne dit pas. Avec un libellé identique à celui des autres entrées,
      // l'aplat serait le seul signal ; avec une teinte seule, WCAG 1.4.1 refuse.
      const palette = jetons(mode);
      assert.notEqual(palette['sur-barre-active'], palette['sur-barre']);
      assert.ok(Math.abs(luminance(palette['sur-barre-active']) - luminance(palette['sur-barre'])) > 0.2,
        'le libellé actif doit différer en LUMINANCE, pas seulement en teinte');
    });
  }
});

describe('les règles non négociables du socle', () => {
  it('garde une cible tactile au-dessus du minimum des deux plateformes', () => {
    const cible = source.match(/--cible:\s*(\d+)px/);
    assert.ok(cible, '--cible absent');
    assert.ok(Number(cible[1]) >= 48,
      '48px est le plancher Material ; 44 celui d\'Apple');
  });

  it('garde la cible minimale à 44px, plancher d\'Apple', () => {
    // Appel taille="petit" en dépend : sans cette garde, la contrainte restait
    // annoncée et non tenue.
    const min = source.match(/--cible-min:\s*(\d+)px/);
    assert.ok(min, '--cible-min absent');
    assert.ok(Number(min[1]) >= 44, '44px est le plancher d\'Apple');
  });

  it('déclare le rythme vertical que `Bande` consomme, en rem', () => {
    // `composants/base.jsx` écrit `py-[var(--pas-16)] sm:py-[var(--pas-24)]` :
    // un jeton renommé ou vidé retire la respiration de chaque section des
    // quatorze pages, sans qu'aucun contraste ne bouge.
    for (const nom of ['pas-16', 'pas-24']) {
      const m = source.match(new RegExp(`--${nom}:\\s*([\\d.]+)rem\\s*;`));
      assert.ok(m, `--${nom} absent ou plus en rem`);
      assert.ok(Number(m[1]) > 0, `--${nom} vaut zéro`);
    }
    assert.ok(Number(source.match(/--pas-24:\s*([\d.]+)rem/)[1])
      > Number(source.match(/--pas-16:\s*([\d.]+)rem/)[1]),
      '--pas-24 doit dépasser --pas-16 : le rythme croît avec la largeur');
  });

  it('respecte le mouvement réduit globalement, pas écran par écran', () => {
    assert.match(source, /@media \(prefers-reduced-motion: reduce\)/);
    assert.match(source, /animation-duration: 0\.01ms !important/);
  });

  it('déclare color-scheme pour que les contrôles natifs suivent le thème', () => {
    assert.match(source, /color-scheme:\s*light dark/);
  });
});

/**
 * Un jeton déclaré et jamais employé finit par servir, et rarement comme son
 * nom le promettait. Vingt-deux dormaient dans `jetons.css` — les familles
 * `--pas-*`, `--txt-*`, `--plan-*`, plus `--interligne-*` et `--rayon-plein` —
 * et ont été retirés le 2026-11-28.
 *
 * <p><strong>Mesurer « jamais employé » est le piège, et je m'y suis pris à
 * trois fois.</strong> Deux faux positifs, chacun prêt à faire supprimer un
 * jeton vivant :
 *
 * <ol>
 * <li>Les jetons du bloc `@theme` de Tailwind ne s'écrivent presque jamais
 *     `var(--color-accent)` : ils engendrent des UTILITAIRES — `bg-accent`,
 *     `text-sur-fond-doux`. Les chercher sous leur nom complet les déclarait
 *     tous morts, les trente-neuf.</li>
 * <li>`--mur-fond` et `--mur-encre` ne vivent que dans `front/app/ecran.html`,
 *     une page d'entrée écrite à la main. En ne balayant que `front/src`, on
 *     les déclarait morts aussi.</li>
 * </ol>
 *
 * <p>Et la direction inverse compte autant : les `.html` de
 * `static/app/` sont CONSTRUITS. Les balayer ferait passer pour vivant un
 * jeton que plus personne n'écrit, jusqu'au prochain build.
 */
describe('aucun jeton ne dort dans la feuille', () => {
  const RACINE = resolve(import.meta.dirname, '../../..');

  /** Les dossiers de sources écrites à la main, et eux seuls. */
  const SOURCES = [
    ['front/src', true], ['front/app', false], ['front/contenu', false],
    ['front', false], ['src/main/resources/static/app', false],
  ];
  const LISIBLES = /\.(jsx|css|html|mjs|js)$/;

  /** Les fichiers d'un dossier, récursivement ou non. */
  function fichiers(dossier, recursif) {
    const out = [];
    let entrees;
    try {
      entrees = readdirSync(resolve(RACINE, dossier), { withFileTypes: true });
    }
    catch { return out; }
    for (const e of entrees) {
      const chemin = `${dossier}/${e.name}`;
      if (e.isDirectory()) {
        if (recursif) out.push(...fichiers(chemin, true));
      }
      // Les `.html` de `static/app` sont CONSTRUITS : les balayer ferait
      // passer pour vivant un jeton que plus personne n'écrit.
      else if (LISIBLES.test(e.name)
        && !(dossier.endsWith('static/app') && e.name.endsWith('.html'))) {
        out.push(chemin);
      }
    }
    return out;
  }

  /**
   * Deux jetons appartiennent au contrat du thème shadcn vendorisé, pas au
   * nôtre : `sidebar-primary` et sa paire. Aucun composant ne les emploie
   * aujourd'hui, mais les retirer casserait silencieusement le prochain
   * composant vendorisé qui les attend.
   */
  const CONTRAT_VENDORISE = new Set([
    '--color-sidebar-primary', '--color-sidebar-primary-foreground',
  ]);

  it('chaque jeton déclaré est employé quelque part', () => {
    const textes = SOURCES.flatMap(([dossier, recursif]) =>
      fichiers(dossier, recursif)
        .map((f) => [f, readFileSync(resolve(RACINE, f), 'utf8')]));
    assert.ok(textes.length > 50, `seulement ${textes.length} sources balayées`);

    const theme = readFileSync(resolve(RACINE, 'front/src/styles.css'), 'utf8');
    const bloc = theme.slice(theme.indexOf('@theme inline {'));
    const dansLeTheme = new Set(
      [...bloc.slice(0, bloc.indexOf('\n}')).matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)]
        .map((m) => m[1]));

    const dorment = [];
    for (const fichier of [JETONS, 'front/src/styles.css']) {
      const declare = readFileSync(resolve(RACINE, fichier), 'utf8');
      for (const [, jeton] of declare.matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)) {
        if (CONTRAT_VENDORISE.has(jeton)) continue;
        const direct = new RegExp(`var\\(\\s*${jeton}\\s*\\)`);
        let vu = textes.some(([, t]) => direct.test(t));
        if (!vu && dansLeTheme.has(jeton)) {
          const utilitaire = jeton.replace(/^--(color|radius|shadow|font|text|spacing)-/, '');
          const motif = new RegExp(`[-:\\[\\s"'\`]${utilitaire}(?![a-z0-9-])`);
          vu = textes.some(([f, t]) => !f.endsWith('styles.css') && motif.test(t));
        }
        if (!vu) dorment.push(jeton);
      }
    }
    assert.deepEqual([...new Set(dorment)], [],
      'des jetons sont déclarés et jamais employés : les retirer, ou les employer');
  });
});

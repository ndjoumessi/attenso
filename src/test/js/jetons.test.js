import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
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

describe('les règles non négociables du socle', () => {
  it('garde une cible tactile au-dessus du minimum des deux plateformes', () => {
    const cible = source.match(/--cible:\s*(\d+)px/);
    assert.ok(cible, '--cible absent');
    assert.ok(Number(cible[1]) >= 48,
      '48px est le plancher Material ; 44 celui d\'Apple');
  });

  it('respecte le mouvement réduit globalement, pas écran par écran', () => {
    assert.match(source, /@media \(prefers-reduced-motion: reduce\)/);
    assert.match(source, /animation-duration: 0\.01ms !important/);
  });

  it('déclare color-scheme pour que les contrôles natifs suivent le thème', () => {
    assert.match(source, /color-scheme:\s*light dark/);
  });
});

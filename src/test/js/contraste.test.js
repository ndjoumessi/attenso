import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const JETONS = readFileSync(
  resolve(import.meta.dirname, '../../main/resources/static/app/jetons.css'), 'utf8');

/** Les deux thèmes, lus séparément : le sombre redéfinit les mêmes noms. */
function palette(sombre) {
  const bloc = sombre
    ? JETONS.slice(JETONS.indexOf('prefers-color-scheme: dark'))
    : JETONS.slice(0, JETONS.indexOf('prefers-color-scheme: dark'));
  const valeurs = {};
  for (const [, nom, hex] of bloc.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    valeurs[nom] = hex;
  }
  return valeurs;
}

const canal = (c) => (c / 255 <= 0.04045 ? c / 255 / 12.92 : ((c / 255 + 0.055) / 1.055) ** 2.4);
const luminance = (hex) => {
  const n = hex.replace('#', '');
  const [r, v, b] = [0, 2, 4].map((i) => parseInt(n.slice(i, i + 2), 16));
  return 0.2126 * canal(r) + 0.7152 * canal(v) + 0.0722 * canal(b);
};
const contraste = (a, b) => {
  const [x, y] = [luminance(a), luminance(b)];
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
};

/**
 * Les ratios sont CALCULÉS, pas lus dans les commentaires.
 *
 * <p>Un commentaire se périme sans bruit quand la valeur change à côté. Au
 * recensement, SIX des trente-neuf commentaires chiffrés de `jetons.css`
 * étaient faux. Cinq sous-estimaient, donc sans danger — mais le sixième
 * allait dans l'autre sens : le thème sombre annonçait 1,4:1 de séparation
 * entre la bande contrastée et le fond, pour 1,26:1 réels.
 */
describe('chaque rôle sémantique passe AA, dans les deux thèmes', () => {
  const ROLES = ['accent', 'alerte', 'attention', 'info'];

  for (const sombre of [false, true]) {
    const mode = sombre ? 'sombre' : 'clair';
    const p = palette(sombre);

    for (const role of ROLES) {
      it(`${role} — ${mode}`, () => {
        assert.ok(p[role], `--${role} absent du thème ${mode}`);
        // Le texte posé SUR l'aplat.
        assert.ok(contraste(p[`sur-${role}`], p[role]) >= 4.5,
          `sur-${role} sur ${role} : ${contraste(p[`sur-${role}`], p[role]).toFixed(2)}:1`);
        // Le texte posé sur le VOILE, qui a son propre ton — un aplat lisible
        // ne l'est pas forcément sur son voile, le thème sombre l'a montré.
        const texte = p[`${role}-texte`] ?? p[role];
        assert.ok(contraste(texte, p[`${role}-voile`]) >= 4.5,
          `${role}-texte sur ${role}-voile : `
          + `${contraste(texte, p[`${role}-voile`]).toFixed(2)}:1`);
      });
    }
  }
});

/**
 * Un commentaire chiffré dit la vérité, ou il n'est pas là.
 *
 * <p>La garde du dessus prouve que les rôles PASSENT ; celle-ci prouve que ce
 * que le fichier RACONTE est exact. Ce sont deux propriétés différentes, et
 * c'est la seconde qui manquait : six commentaires sur trente-neuf étaient
 * faux, dont un qui surestimait.
 *
 * <p>La référence est DÉDUITE de la convention de nommage du fichier —
 * `sur-X` se mesure sur `X`, `X-texte` sur `X-voile`, un rôle sémantique sur
 * `--fond`. Une table se périmerait à chaque jeton ajouté ; la convention,
 * elle, est déjà ce que le fichier respecte.
 *
 * <p>Pas de repli attrape-tout : un nom dont la référence ne se déduit pas
 * fait ÉCHOUER le test. Ma première version repliait sur `--fond`, ce qui
 * mesurait en silence contre la mauvaise référence et rendait ce cas
 * invisible. Un commentaire qu'on ne sait pas vérifier est exactement celui
 * qui dérivera.
 *
 * <p>Vérifié par mutation : un jeton chiffré de référence inconnue rougit.
 * La branche explicite ci-dessous n'est pas ce qui le fait rougir — sans
 * elle le calcul lève — mais c'est elle qui rend l'échec LISIBLE au lieu
 * d'une exception.
 */
describe('chaque ratio écrit dans jetons.css est exact', () => {
  /** Les jetons qui se mesurent sur le fond de la page. */
  const CONTRE_FOND = new Set([
    'accent', 'info', 'alerte', 'attention', 'bordure-controle', 'bande-contraste',
  ]);

  /** `sur-X` → `X`, `X-texte` → `X-voile`, `mur-encre*` → `mur-fond`. */
  function reference(nom, commentaire, p) {
    const nomme = commentaire.match(/--([a-z0-9-]+)/);
    if (nomme && p[nomme[1]]) return nomme[1];
    if (/blanc dessus/.test(commentaire)) return '#ffffff';
    if (nom.startsWith('sur-')) {
      let base = nom.slice(4);
      for (const suffixe of ['-doux', '-douce']) {
        if (base.endsWith(suffixe) && p[base.slice(0, -suffixe.length)]) {
          base = base.slice(0, -suffixe.length);
        }
      }
      if (p[base]) return base;
    }
    if (nom.startsWith('mur-encre')) return 'mur-fond';
    if (nom.endsWith('-texte') && p[`${nom.slice(0, -6)}-voile`]) return `${nom.slice(0, -6)}-voile`;
    // Énumérés, et non repliés par défaut : un nom inconnu doit rougir, pas
    // être mesuré en silence contre la mauvaise référence.
    if (CONTRE_FOND.has(nom)) return 'fond';
    return null;
  }

  for (const sombre of [false, true]) {
    const mode = sombre ? 'sombre' : 'clair';
    it(`thème ${mode}`, () => {
      const p = palette(sombre);
      const bloc = sombre
        ? JETONS.slice(JETONS.indexOf('prefers-color-scheme: dark'))
        : JETONS.slice(0, JETONS.indexOf('prefers-color-scheme: dark'));
      const faux = [];
      let vus = 0;
      for (const ligne of bloc.split('\n')) {
        const m = ligne.match(/^\s*--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;\s*\/\*(.*)/);
        if (!m) continue;
        const [, nom, hex, commentaire] = m;
        const chiffre = commentaire.match(/(\d+[.,]\d+|\d+)\s*:\s*1/);
        if (!chiffre) continue;
        vus += 1;
        const cle = reference(nom, commentaire, p);
        const cible = cle.startsWith('#') ? cle : p[cle];
        if (!cle || !cible) {
          faux.push(`--${nom} : référence indéduisible, ratio invérifiable`);
          continue;
        }
        const dit = Number(chiffre[1].replace(',', '.'));
        const calcule = Math.round(contraste(hex, cible) * 100) / 100;
        if (Math.abs(calcule - dit) >= 0.005) {
          faux.push(`--${nom} contre --${cle} : annoncé ${dit}, calculé ${calcule}`);
        }
      }
      assert.ok(vus > 0, `aucun ratio chiffré lu dans le thème ${mode}`);
      assert.deepEqual(faux, [], `des commentaires de jetons.css mentent (${vus} lus)`);
    });
  }
});

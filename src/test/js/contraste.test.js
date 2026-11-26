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
 * <p>Un commentaire se périme sans bruit quand la valeur change à côté : deux
 * de ceux de ce fichier sous-estimaient déjà le réel — 6,46 annoncé pour 6,68,
 * 10,2 pour 11,16. Sans danger dans ce sens-là, mais rien ne garantissait
 * l'autre sens.
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

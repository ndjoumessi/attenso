/**
 * Les balises `<button …>` d'un source JSX, en suivant la profondeur des
 * accolades — partagé, parce que deux gardes en ont besoin et qu'une copie
 * divergerait.
 *
 * <p>Pourquoi pas `/<button[\s\S]*?>/` : ce motif s'arrête au `>` de
 * `onClick={() => x}` et rend une balise TRONQUÉE. Mesuré sur ce dépôt :
 * 10 des 27 balises sont amputées par le motif naïf. Une garde qui lit la
 * mauvaise étendue ne garde rien — elle avait déjà déclaré deux boutons
 * dépourvus d'un geste qu'ils portaient.
 */
export function balises(source, nom = 'button') {
  const out = [];
  for (const m of source.matchAll(new RegExp(`<${nom}\\b`, 'g'))) {
    let i = m.index + nom.length + 1;
    let profondeur = 0;
    while (i < source.length) {
      const c = source[i];
      if (c === '{') profondeur += 1;
      else if (c === '}') profondeur -= 1;
      else if (c === '>' && profondeur === 0 && source[i - 1] !== '=') {
        out.push(source.slice(m.index, i + 1));
        break;
      }
      i += 1;
    }
  }
  return out;
}

/** Les `<button>` seuls — le cas de loin le plus fréquent. */
export const balisesButton = (source) => balises(source, 'button');

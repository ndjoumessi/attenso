// Efface les sorties de la construction précédente.
//
// Vite ne vide pas le répertoire de sortie ici (emptyOutDir est faux), et
// c'est volontaire : il contient aussi des fichiers qui ne viennent pas de
// lui — les icônes, le manifeste d'application. Mais sans ce ménage, chaque
// build empile ses fichiers hachés : la coquille hors réseau a ramassé
// jusqu'à dix versions mortes de la même feuille de style.
import { rm } from 'node:fs/promises';
import { resolve } from 'node:path';

const STATIQUE = resolve(import.meta.dirname, '../src/main/resources/static');
await rm(resolve(STATIQUE, 'app/assets'), { recursive: true, force: true });

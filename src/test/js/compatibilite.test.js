import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { describe, it } from 'node:test';

const CONFIG = 'front/vite.config.js';
const ACTIFS = 'src/main/resources/static/app/assets';

/**
 * Le plancher que ce projet doit tenir, d'après ENF-08 : « Safari iOS 16+,
 * Chrome Android et bureau, deux dernières versions ». Il est écrit ici plutôt
 * que lu dans le cahier des charges parce que docs/ n'est pas publié : un test
 * qui en dépendrait serait rouge en intégration continue.
 */
const SAFARI_ENF_08 = 16.0;

/**
 * Fonctionnalités CSS et la version de Safari qui les apporte. Sert à vérifier
 * que la cible DÉCLARÉE ne ment pas sur ce que la feuille produite exige.
 */
const CSS_EXIGE = [
  ['@property', 16.4],
  ['color-mix(', 16.2],
  ['@container', 16.0],
  [':has(', 15.4],
  ['oklch(', 15.4],
  ['@scope', 17.4],
  ['text-wrap: balance', 17.5],
];

/**
 * Syntaxe JavaScript postérieure à Safari 16.0. Motifs choisis pour ne pas
 * apparaître par accident dans une chaîne de caractères — le balayage est un
 * détecteur de fumée, pas un analyseur syntaxique, et on le dit.
 */
const JS_APRES_16 = [
  [/\bstatic\s*\{/, 'blocs statiques de classe', 16.4],
  [/\.toSorted\(/, 'Array.prototype.toSorted', 16.4],
  [/\.toSpliced\(/, 'Array.prototype.toSpliced', 16.4],
  [/\bObject\.groupBy\(/, 'Object.groupBy', 17.4],
  [/\bArray\.fromAsync\(/, 'Array.fromAsync', 18.4],
];

/** Les cibles déclarées dans la configuration de construction. */
function ciblesDeclarees() {
  const source = readFileSync(CONFIG, 'utf8');
  const lire = (cle) => {
    const bloc = source.match(new RegExp(`^\\s*${cle}:\\s*\\[([^\\]]*)\\]`, 'm'));
    return bloc
      ? bloc[1].split(',').map((m) => m.trim().replace(/^['"]|['"]$/g, '')).filter(Boolean)
      : null;
  };
  return { target: lire('target'), cssTarget: lire('cssTarget') };
}

/** Le plancher Safari d'une liste de cibles esbuild : ['safari16.4'] -> 16.4 */
function plancherSafari(cibles) {
  const safari = cibles.filter((c) => c.startsWith('safari'));
  assert.ok(safari.length === 1, `une seule cible safari attendue, vu : ${cibles}`);
  const version = Number(safari[0].slice('safari'.length));
  assert.ok(Number.isFinite(version), `version safari illisible : ${safari[0]}`);
  return version;
}

function actifs(extension) {
  assert.ok(existsSync(ACTIFS), `${ACTIFS} absent : construire le front d'abord`);
  const fichiers = readdirSync(ACTIFS).filter((f) => f.endsWith(extension));
  assert.ok(fichiers.length > 0, `aucun fichier ${extension} produit`);
  return fichiers.map((f) => ({ nom: f, code: readFileSync(`${ACTIFS}/${f}`, 'utf8') }));
}

describe('le plancher de compatibilité est une décision, pas un défaut', () => {
  it('déclare ses cibles au lieu de suivre la fenêtre glissante de Vite', () => {
    // Sans déclaration, Vite applique « baseline-widely-available », qui avance
    // tout seul avec le temps : le plancher monterait sans qu'une seule ligne
    // change ici, et la tablette de l'accueil casserait sans test rouge.
    const { target, cssTarget } = ciblesDeclarees();
    assert.ok(target, 'build.target non déclaré dans vite.config.js');
    assert.ok(cssTarget, 'build.cssTarget non déclaré dans vite.config.js');
  });

  it('tient le plancher Safari que demande ENF-08 pour le JavaScript', () => {
    const plancher = plancherSafari(ciblesDeclarees().target);
    assert.ok(plancher <= SAFARI_ENF_08,
      `cible safari${plancher} : ENF-08 demande ${SAFARI_ENF_08} ou plus bas`);
  });

  it('ne ment pas sur le plancher du CSS, qui est plus haut', () => {
    // Tailwind 4 impose Safari 16.4. L'écart avec ENF-08 est assumé et écrit
    // dans la configuration ; ce test exige seulement que la valeur déclarée
    // couvre ce que la feuille PRODUITE utilise réellement. Si quelqu'un y
    // introduit une fonctionnalité plus récente sans relever la cible, la
    // déclaration devient fausse et ce test le dit.
    const declare = plancherSafari(ciblesDeclarees().cssTarget);
    const feuilles = actifs('.css');

    for (const [fonction, exige] of CSS_EXIGE) {
      const present = feuilles.some(({ code }) => code.includes(fonction));
      if (present) {
        assert.ok(declare >= exige,
          `la feuille utilise « ${fonction} » (Safari ${exige}) `
          + `alors que cssTarget annonce safari${declare}`);
      }
    }
  });

  it("ne livre pas de syntaxe JavaScript que le plancher déclaré ne sait pas lire", () => {
    // Détecteur de fumée : il ne remplace pas un analyseur syntaxique, mais il
    // attrape le cas qui arriverait vraiment — une dépendance mise à jour qui
    // livre du code plus récent que la cible, sans que la construction bronche.
    const plancher = plancherSafari(ciblesDeclarees().target);

    for (const { nom, code } of actifs('.js')) {
      for (const [motif, nomFonction, exige] of JS_APRES_16) {
        if (exige > plancher) {
          assert.ok(!motif.test(code),
            `${nom} contient ${nomFonction} (Safari ${exige}), `
            + `au-dessus du plancher safari${plancher}`);
        }
      }
    }
  });
});

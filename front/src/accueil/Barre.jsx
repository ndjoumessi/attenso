import { PanelLeftIcon } from 'lucide-react';
import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent,
  SidebarGroupLabel, SidebarHeader, SidebarMenu, SidebarMenuBadge,
  SidebarMenuButton, SidebarMenuItem, SidebarSeparator, useSidebar,
} from '@/ui/sidebar';
import { GROUPES, VUES } from './navigation.js';
import { CLE_CLINIQUE } from './session.js';
import {
  ANNEAU, PRESSE,
} from '../composants/formulaire.jsx';

/**
 * La barre latérale du tableau du personnel.
 *
 * <p>À LIBELLÉS, toujours. Sous le seuil de la barre (900 px, déclaré une fois
 * en `--breakpoint-barre`) elle devient un TIROIR que l'on ouvre, et jamais un
 * rail d'icônes : des pictogrammes muets face à quelqu'un qui n'a pas appris le
 * vocabulaire est le contraire d'une démonstration réussie, et le survol
 * n'existe pas sur une tablette. Les trois formes que prend ce repli chez
 * shadcn — le mode réduit en icônes, le rail de redimensionnement et
 * l'infobulle qui remplace le libellé — sont donc toutes écartées, et
 * `navigation.test.js` monte la garde pour qu'elles ne reviennent pas.
 */
export function Barre({ identite, vueCourante, enFile, surChangement }) {
  const { isMobile, setOpenMobile } = useSidebar();

  // Sur un tiroir, le clic doit AUSSI refermer : laisser le tiroir ouvert sur
  // la vue qu'on vient de demander la cache derrière son propre menu.
  const aller = (cle) => {
    surChangement(cle);
    if (isMobile) setOpenMobile(false);
  };

  const entree = (vue) => (
    <Entree key={vue.cle} vue={vue} active={vue.cle === vueCourante}
            compteur={vue.cle === 'file' ? enFile : null} surClic={aller} />
  );

  const clinique = typeof localStorage === 'undefined' ? 'Attenso'
    : localStorage.getItem(CLE_CLINIQUE) ?? 'Attenso';

  return (
    <Sidebar collapsible="offcanvas">
      {/* Le dégagement de la croix de fermeture, et SEULEMENT dans le tiroir.
          Elle est posée en `absolute top-4 right-4` par `sheet.jsx` et mesure
          44 px, donc elle occupe les 60 premiers pixels depuis le bord droit :
          `pr-16` (64 px) la dégage avec un cheveu de marge. Au-dessus du seuil
          il n'y a pas de croix, et prendre 64 px sur un en-tête de 256 px
          ferait replier le nom de la clinique pour rien. */}
      <SidebarHeader className={`px-3 py-4 ${isMobile ? 'pr-16' : ''}`}>
        <p className="text-base font-bold leading-tight">{clinique}</p>
      </SidebarHeader>
      <SidebarSeparator />
      <SidebarContent>
        {/* La file est ÉPINGLÉE au-dessus des groupes : elle est l'écran par
            défaut, pas une destination parmi d'autres. */}
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>{VUES.filter((v) => v.groupe === null).map(entree)}</SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
        {GROUPES.map((groupe) => (
          <SidebarGroup key={groupe}>
            <SidebarGroupLabel className="px-3 uppercase tracking-wide
                                          text-sur-barre-doux">
              {groupe}
            </SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {VUES.filter((v) => v.groupe === groupe).map(entree)}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
      <SidebarSeparator />
      <SidebarFooter className="px-3 py-3">
        {/* Qui est connecté, sur une tablette de comptoir partagée. Le départ
            reste dans l'en-tête de l'écran, avec son avertissement. */}
        <p className="text-sm text-sur-barre-doux">{identite}</p>
        {/* La seule sortie du tableau était « Se déconnecter » : pour aller
            voir le site, il fallait fermer sa session. Un LIEN, donc, et
            surtout pas un bouton qui quitte — les gestes en attente de rejeu
            partiraient avec. */}
        {/* Sans flèche : ce n'est pas un RETOUR mais un lien latéral vers le
            site public, et la flèche de retour appartient à la coquille
            partagée — sa garde me l'a rappelé en rougissant. */}
        <a href="/" className={`cible-min mt-1 inline-flex items-center text-sm
                               text-sur-barre-doux underline underline-offset-2 ${ANNEAU}`}>
          Le site Attenso
        </a>
      </SidebarFooter>
    </Sidebar>
  );
}

/**
 * Une entrée de la barre : glyphe décoratif, LIBELLÉ, et son compteur.
 *
 * <p>`rounded` et non le `rounded-md` par défaut de shadcn : le rayon du
 * projet est `--rayon`, et `rounded-md` est une valeur de Tailwind qui n'y est
 * pas branchée.
 *
 * <p>Le compteur est le seul texte posé sur l'aplat de l'entrée active. Il
 * hérite donc de `--sur-barre-active`, la seule paire MESURÉE contre
 * `--barre-active` : un libellé doux y donnerait 1,05:1 en clair.
 */
function Entree({ vue, active, compteur, surClic }) {
  const Icone = vue.icone;
  return (
    <SidebarMenuItem>
      <SidebarMenuButton size="lg" isActive={active}
                         aria-current={active ? 'page' : undefined}
                         onClick={() => surClic(vue.cle)}
                         className="cible-min rounded px-3 text-base">
        {/* Taille et retrait laissés aux variantes du bouton, qui portent déjà
            `[&>svg]:size-4` et `[&>svg]:shrink-0` : les redéclarer ici perdrait
            la bataille de spécificité contre elles, sans le dire. */}
        <Icone aria-hidden="true" />
        <span>{vue.libelle}</span>
      </SidebarMenuButton>
      {compteur > 0 && (
        <SidebarMenuBadge className="rounded font-bold tabular-nums">
          {compteur}
        </SidebarMenuBadge>
      )}
    </SidebarMenuItem>
  );
}

/**
 * Le bouton qui ouvre le tiroir, sous le seuil de la barre.
 *
 * <p>Il porte le mot « Menu » et non la seule icône de panneau de shadcn : le
 * geste d'ouverture doit se lire comme les entrées qu'il révèle.
 *
 * <p>Sa visibilité vient d'`isMobile` — le MÊME prédicat JavaScript que celui
 * qui décide ce que fait `toggleSidebar` — et non d'une classe `barre:hidden`.
 * C'est la correction d'un défaut : avec la classe, l'apparition du bouton
 * venait du CSS et son effet du JavaScript, deux sources qui pouvaient se
 * contredire. Elles se contredisaient : sur Safari iOS sans syntaxe
 * d'intervalle, le bouton s'affichait au-dessus de 900 px alors que
 * `toggleSidebar` partait vers le panneau permanent, et rien ne s'ouvrait. Liés
 * à une seule source, « bouton visible » et « tiroir ouvrable » ne peuvent plus
 * diverger.
 */
export function OuvreLaBarre() {
  const { isMobile, toggleSidebar } = useSidebar();
  if (!isMobile) return null;
  return (
    <button type="button" onClick={toggleSidebar}
            className={`cible-min mb-4 inline-flex items-center gap-2 rounded border
                       border-bordure-controle bg-surface px-4 font-semibold
                       hover:bg-surface-douce ${PRESSE} ${ANNEAU}`}>
      <PanelLeftIcon aria-hidden="true" className="size-5" />
      Menu
    </button>
  );
}

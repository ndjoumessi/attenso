import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent,
  SidebarGroupLabel, SidebarHeader, SidebarMenu, SidebarMenuBadge,
  SidebarMenuButton, SidebarMenuItem, SidebarSeparator, useSidebar,
} from '@/ui/sidebar';
import { GROUPES, VUES } from './navigation.js';
import { CLE_CLINIQUE } from './session.js';

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
      <SidebarHeader className="px-3 py-4">
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
  return (
    <SidebarMenuItem>
      <SidebarMenuButton size="lg" isActive={active}
                         aria-current={active ? 'page' : undefined}
                         onClick={() => surClic(vue.cle)}
                         className="cible-min rounded px-3 text-base">
        <span aria-hidden="true" className="w-5 shrink-0 text-center">{vue.icone}</span>
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
 * geste d'ouverture doit se lire comme les entrées qu'il révèle. Au-dessus du
 * seuil la barre est là, donc le bouton disparaît.
 */
export function OuvreLaBarre() {
  const { toggleSidebar } = useSidebar();
  return (
    <button type="button" onClick={toggleSidebar} aria-label="Ouvrir le menu"
            className="cible-min mb-4 inline-flex items-center gap-2 rounded border
                       border-bordure-controle bg-surface px-4 font-semibold
                       hover:bg-surface-douce barre:hidden">
      <span aria-hidden="true">☰</span>
      Menu
    </button>
  );
}

import { hydrateRoot } from 'react-dom/client';
import { Comptoir } from './comptoir.jsx';

// HYDRATATION et non montage : le formulaire est déjà dans le HTML, pré-rendu
// à la construction. Le client le voit et peut commencer à taper avant que ce
// fichier soit arrivé — ce qui compte sur un téléphone mal capté, dans une
// rue, qui est la situation normale de cette page.
hydrateRoot(document.getElementById('racine'), <Comptoir />);

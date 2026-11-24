import { createRoot } from 'react-dom/client';
import { Ecran } from './ecran.jsx';

// Le jeton d'appairage, lu dans l'URL collée une fois dans le téléviseur.
const jeton = location.pathname.split('/').filter(Boolean)[1];

createRoot(document.getElementById('racine')).render(<Ecran jeton={jeton} />);

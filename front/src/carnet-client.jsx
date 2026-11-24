import { hydrateRoot } from 'react-dom/client';
import { Carnet } from './carnet.jsx';

hydrateRoot(document.getElementById('racine'), <Carnet />);

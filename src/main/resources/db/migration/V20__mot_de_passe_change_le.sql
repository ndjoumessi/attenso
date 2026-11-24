-- Invalider les sessions ouvertes quand un mot de passe change.
--
-- Le cas qui compte : on change son mot de passe PARCE QU'ON LE CROIT VOLÉ.
-- Sans cette colonne, le voleur gardait sa session jusqu'à douze heures — la
-- durée d'un jeton du personnel — c'est-à-dire tout le temps qu'il lui
-- fallait. Un mot de passe changé qui ne ferme pas la porte ne sert à rien.
ALTER TABLE utilisateur ADD COLUMN mdp_change_le timestamptz;

COMMENT ON COLUMN utilisateur.mdp_change_le IS
  'Dernier changement de mot de passe. Un jeton émis AVANT cette date est '
  'refusé. NULL pour les comptes dont le mot de passe n''a jamais changé.';

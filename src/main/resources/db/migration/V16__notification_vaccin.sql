-- EF-906 : le rappel de vaccin a sa propre référence.
--
-- Le faire passer par « rendez_vous_id » aurait été une erreur silencieuse —
-- la clé étrangère l'aurait refusé en production, mais seulement le jour du
-- premier rappel, des mois après la mise en service.
ALTER TABLE notification ADD COLUMN vaccination_id uuid REFERENCES vaccination(id);

-- Une notification porte toujours sur UN objet, désormais parmi trois.
ALTER TABLE notification DROP CONSTRAINT notification_un_seul_objet;
ALTER TABLE notification ADD CONSTRAINT notification_un_seul_objet CHECK (
  (ticket_id IS NOT NULL)::int
  + (rendez_vous_id IS NOT NULL)::int
  + (vaccination_id IS NOT NULL)::int = 1);

-- Le plafond d'EF-303, transposé : un seul rappel par vaccination.
CREATE UNIQUE INDEX notification_plafond_vaccin ON notification (vaccination_id, type)
  WHERE vaccination_id IS NOT NULL;

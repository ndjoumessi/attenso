-- EF-905 : le rappel J-1 passe par la table « notification », comme tout le
-- reste. Une seconde file d'envoi ferait diverger deux fois le même code, et
-- surtout le coût des SMS de rendez-vous échapperait à ENF-10 et au calcul de
-- marge du §13.
ALTER TABLE notification ALTER COLUMN ticket_id DROP NOT NULL;
ALTER TABLE notification ADD COLUMN rendez_vous_id uuid REFERENCES rendez_vous(id);

-- Une notification porte sur UN objet, jamais deux ni zéro. Sans cette
-- contrainte, une ligne orpheline partirait sans qu'on sache à qui.
ALTER TABLE notification ADD CONSTRAINT notification_un_seul_objet
  CHECK ((ticket_id IS NOT NULL) <> (rendez_vous_id IS NOT NULL));

ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
  CHECK (type IN ('TICKET', 'PREVENIR', 'CORRECTIF', 'RAPPEL_RDV', 'RAPPEL_VACCIN'));

-- Le plafond d'EF-303, transposé aux rendez-vous : un seul rappel par
-- rendez-vous, garanti par la base et non par le code. L'index existant sur
-- (ticket_id, type) continue de jouer son rôle — PostgreSQL n'y fait pas
-- entrer les NULL en collision.
CREATE UNIQUE INDEX notification_plafond_rdv ON notification (rendez_vous_id, type)
  WHERE rendez_vous_id IS NOT NULL;

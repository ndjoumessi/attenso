-- Preuve de l'acceptation des conditions générales.
--
-- Une case cochée ne vaut rien si l'on ne peut pas dire QUAND elle l'a été :
-- en cas de contestation, c'est l'horodatage qui fait la preuve, pas le
-- souvenir d'une case. La colonne est nullable pour les cliniques créées
-- avant cette migration — les réécrire avec une date inventée serait
-- fabriquer la preuve qu'on prétend conserver.
ALTER TABLE tenant ADD COLUMN cgv_acceptees_le timestamptz;

COMMENT ON COLUMN tenant.cgv_acceptees_le IS
  'Horodatage de l''acceptation des CGV à l''inscription. NULL pour les '
  'établissements créés avant la mise en vente.';

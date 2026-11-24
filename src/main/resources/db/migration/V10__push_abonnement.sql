-- EF-307 : bascule sur notification push pour les clients ayant installé la PWA.
--
-- L'abonnement est attaché au TICKET et non au navigateur. Deux raisons, et la
-- seconde est la bonne :
--   1. c'est le ticket qu'on notifie, pas une personne ;
--   2. EC-03 impose la minimisation, et un abonnement push est un identifiant
--      stable de navigateur. Lié au ticket, il est purgé avec lui à J+1, comme
--      le numéro de mobile. Un abonnement persistant ferait de ce produit un
--      traceur, ce qu'il n'est pas.
CREATE TABLE push_abonnement (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  ticket_id   uuid NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
  endpoint    text NOT NULL,
  cle_p256dh  text NOT NULL,
  cle_auth    text NOT NULL,
  cree_le     timestamptz NOT NULL DEFAULT now(),

  -- Un seul abonnement par ticket : si le client se réabonne (nouvelle
  -- autorisation, autre navigateur), c'est le dernier qui compte. Deux
  -- abonnements enverraient deux notifications pour un seul tour.
  CONSTRAINT push_un_par_ticket UNIQUE (ticket_id)
);

ALTER TABLE push_abonnement ENABLE ROW LEVEL SECURITY;
CREATE POLICY push_abonnement_cloisonnement ON push_abonnement
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON push_abonnement TO ${roleApplicatif};

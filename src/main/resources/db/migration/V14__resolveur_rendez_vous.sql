-- EF-905 : résoudre la clinique d'une référence de rendez-vous.
--
-- Même mécanisme que pour les tickets, et pour la même raison : il faut
-- connaître la clinique AVANT de pouvoir lier le tenant, donc avant que le RLS
-- ne s'applique. Une fonction SECURITY DEFINER est le seul chemin de lecture
-- hors cloisonnement, et elle ne rend qu'un identifiant de clinique, à qui
-- détient déjà la référence.
--
-- L'alternative — balayer toutes les cliniques jusqu'à trouver — serait
-- correcte mais dirait à l'appelant, par le temps de réponse, combien de
-- cliniques existent et où se trouve la sienne dans la liste.
CREATE OR REPLACE FUNCTION attenso_tenant_de_reference_rdv(ref text) RETURNS uuid
  LANGUAGE sql STABLE SECURITY DEFINER
  SET search_path = public, pg_temp
AS $$ SELECT tenant_id FROM rendez_vous WHERE reference = ref $$;

REVOKE ALL ON FUNCTION attenso_tenant_de_reference_rdv(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION attenso_tenant_de_reference_rdv(text) TO ${roleApplicatif};

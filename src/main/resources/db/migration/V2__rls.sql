-- Seconde barrière d'isolation : row level security.
--
-- Pourquoi maintenant et pas plus tard : la première barrière est le
-- `tenant_id` passé dans chaque requête. Elle tient tant que personne
-- n'oublie un `WHERE`. Celle-ci tient même en cas d'oubli — mais seulement si
-- l'application se connecte avec un rôle NON propriétaire, car le
-- propriétaire contourne les politiques. D'où les deux rôles de compose.yaml.
--
-- Fermeture par défaut : si `attenso.tenant_id` n'est pas positionné, la
-- comparaison vaut NULL et AUCUNE ligne n'est visible. Un oubli de binding
-- donne donc une liste vide, pas une fuite.
--
-- `tenant` est volontairement exclu : il faut pouvoir résoudre le slug d'un
-- QR code AVANT de savoir de quel tenant il s'agit.

CREATE OR REPLACE FUNCTION attenso_tenant_courant() RETURNS uuid
  LANGUAGE sql STABLE AS
$$ SELECT nullif(current_setting('attenso.tenant_id', true), '')::uuid $$;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['ticket', 'notification', 'utilisateur', 'journal_acces', 'commande']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (tenant_id = attenso_tenant_courant()) '
      || 'WITH CHECK (tenant_id = attenso_tenant_courant())',
      t || '_cloisonnement', t);
  END LOOP;
END
$$;

-- Résolution du tenant depuis la référence d'un lien SMS.
--
-- SECURITY DEFINER, donc hors RLS : il faut connaître le tenant AVANT de
-- pouvoir le lier, et sans ça le lien du SMS devrait porter le slug de la
-- clinique — des caractères en plus dans un message qui doit tenir en un
-- segment. La fonction ne rend qu'un identifiant de clinique, et seulement à
-- qui détient déjà la référence : c'est exactement ce que le client est en
-- droit de savoir.
CREATE OR REPLACE FUNCTION attenso_tenant_de_reference(ref text) RETURNS uuid
  LANGUAGE sql STABLE SECURITY DEFINER
  SET search_path = public, pg_temp
AS $$ SELECT tenant_id FROM ticket WHERE reference = ref $$;

REVOKE ALL ON FUNCTION attenso_tenant_de_reference(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION attenso_tenant_de_reference(text) TO ${roleApplicatif};

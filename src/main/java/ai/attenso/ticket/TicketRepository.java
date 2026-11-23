package ai.attenso.ticket;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

	/**
	 * Le calcul de position, et la seule définition qui en existe. Position 1
	 * = le prochain appelé. Les états listés doivent rester identiques au
	 * prédicat de {@code ticket_file} et à {@link Statut#OUVERTS}.
	 */
	@Query(value = """
			SELECT count(*) FROM ticket
			WHERE tenant_id = :tenant AND jour = :jour
			  AND statut IN ('EN_ATTENTE','PREVENU','APPELE','ABSENT')
			  AND (priorite > :priorite OR (priorite = :priorite AND ordre < :ordre))
			""", nativeQuery = true)
	long compterDevant(@Param("tenant") UUID tenant, @Param("jour") LocalDate jour,
			@Param("priorite") short priorite, @Param("ordre") long ordre);

	List<Ticket> findByTenantIdAndJourAndStatutInOrderByPrioriteDescOrdreAsc(
			UUID tenantId, LocalDate jour, Collection<Statut> statuts);

	List<Ticket> findByTenantIdAndJourOrderByPrioriteDescOrdreAsc(UUID tenantId, LocalDate jour);

	Optional<Ticket> findByIdAndTenantId(UUID id, UUID tenantId);

	/** Index unique {@code ticket.reference} : une égalité, pas un balayage. */
	Optional<Ticket> findByReference(String reference);

	List<Ticket> findByTenantIdAndStatutAndGraceExpireLeBefore(
			UUID tenantId, Statut statut, Instant limite);

	long countByTenantIdAndJourAndStatut(UUID tenantId, LocalDate jour, Statut statut);

	long countByTenantIdAndJour(UUID tenantId, LocalDate jour);

	long countByTenantIdAndJourAndTelephoneE164(UUID tenantId, LocalDate jour, String telephone);

	boolean existsByTenantIdAndJourAndOrdre(UUID tenantId, LocalDate jour, long ordre);

	@Query("""
			select coalesce(max(t.numeroJour), 0) from Ticket t
			where t.tenantId = :tenant and t.jour = :jour
			""")
	int maxNumeroJour(@Param("tenant") UUID tenant, @Param("jour") LocalDate jour);

	/** Durées de consultation réelles, les plus récentes d'abord, en secondes. */
	@Query(value = """
			SELECT extract(epoch FROM (clos_le - consultation_le)) FROM ticket
			WHERE tenant_id = :tenant AND statut = 'TERMINE'
			  AND consultation_le IS NOT NULL AND clos_le IS NOT NULL
			ORDER BY clos_le DESC LIMIT :limite
			""", nativeQuery = true)
	List<Double> dernieresDureesSecondes(@Param("tenant") UUID tenant, @Param("limite") int limite);

	/**
	 * Purge RGPD à J+1. La ligne reste, sans numéro : les statistiques de
	 * départs survivent à la purge, puisque c'est elles qui portent la preuve
	 * de valeur.
	 */
	@Modifying
	@Query("""
			update Ticket t set t.telephoneE164 = null
			where t.tenantId = :tenant and t.jour < :jour and t.telephoneE164 is not null
			""")
	int purgerTelephones(@Param("tenant") UUID tenant, @Param("jour") LocalDate jour);
}

package ai.attenso.amorce;

import ai.attenso.agenda.AgendaDao;
import ai.attenso.carnet.CarnetDao;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.NomExpediteur;
import ai.attenso.priorite.MotifDao;
import ai.attenso.securite.GenerateurReference;
import ai.attenso.tenant.TenantRepository;
import ai.attenso.ticket.Origine;
import ai.attenso.ticket.Priorite;
import ai.attenso.ticket.Statut;
import ai.attenso.ticket.Ticket;
import ai.attenso.utilisateur.Role;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée la clinique du pilote et son compte d'accueil, une fois.
 *
 * <p>Idempotent : relancer ne fait rien si le slug existe déjà. À l'installation,
 * elle sème aussi de quoi remplir les écrans de la démonstration
 * ({@link #semerLaDemonstration}). Les écritures
 * passent par {@code JdbcTemplate} parce qu'au démarrage aucun tenant n'est
 * lié, donc les politiques RLS masqueraient la table {@code utilisateur} — et
 * c'est exactement ce qu'on veut qu'elles fassent le reste du temps.
 */
@Component
@ConditionalOnProperty(name = "attenso.amorce.active", havingValue = "true")
public class AmorceClinique implements ApplicationRunner {

	private static final Logger JOURNAL = LoggerFactory.getLogger(AmorceClinique.class);

	private final ProprietesAmorce proprietes;

	private final TenantRepository tenants;

	private final JdbcTemplate jdbc;

	private final PasswordEncoder motsDePasse;

	private final GenerateurReference jetons;

	private final MotifDao motifs;

	private final AgendaDao agenda;

	private final CarnetDao carnets;

	private final Clock horloge;

	public AmorceClinique(ProprietesAmorce proprietes, TenantRepository tenants,
			JdbcTemplate jdbc, PasswordEncoder motsDePasse, GenerateurReference jetons,
			MotifDao motifs, AgendaDao agenda, CarnetDao carnets, Clock horloge) {
		this.proprietes = proprietes;
		this.tenants = tenants;
		this.jdbc = jdbc;
		this.motsDePasse = motsDePasse;
		this.jetons = jetons;
		this.motifs = motifs;
		this.agenda = agenda;
		this.carnets = carnets;
		this.horloge = horloge;
	}

	private static final String MOTIF_CHAT_EN_CAISSE = "Chat stressé en caisse";

	private static final short RANG_CHAT_EN_CAISSE = 7;

	/**
	 * Motifs propres à la clinique de démonstration, en plus des deux défauts.
	 *
	 * <p>Les défauts portent sur l'humain accompagnant et sont posés au même
	 * rang : une clinique qui les voit seuls ne perçoit ni que la priorité est
	 * graduée, ni qu'elle peut écrire ses propres motifs. Ceux-ci parlent de
	 * l'animal, avec des rangs étagés. Ils restent ici et non dans
	 * {@code MotifDao.DEFAUTS} : ils ne conviennent pas à toutes les cliniques,
	 * et changer les défauts changerait le produit pour tous.
	 *
	 * <p>Pas de garde propre : on n'arrive ici que par la création de la
	 * clinique, que {@code run} ne refait jamais quand le slug existe déjà.
	 * Les rangs restent dans la bande 1 à 9 de {@code motif_rang_dans_la_bande} :
	 * aucun ne peut rejoindre l'urgence (10).
	 */
	private void poserLesMotifsDeDemonstration(UUID tenantId) {
		// « Contagieux » tout en haut : l'isoler protège toute la salle d'attente,
		// pas seulement l'animal concerné.
		// « Chat en caisse » : un chat qui s'agite dans sa caisse de transport, que
		// V8__motifs_priorite.sql cite comme exemple de motif propre à une clinique.
		// « Contrôle post-opératoire » en dessous des défauts : c'est du confort,
		// pas une dégradation, il passe devant la file normale sans doubler personne
		// de plus pressé.
		short ordre = (short) (MotifDao.DEFAUTS.size() + 1);
		motifs.ajouter(tenantId, "Animal potentiellement contagieux", (short) 8, ordre++);
		motifs.ajouter(tenantId, MOTIF_CHAT_EN_CAISSE, RANG_CHAT_EN_CAISSE, ordre++);
		motifs.ajouter(tenantId, "Contrôle post-opératoire court", (short) 3, ordre);
	}

	/** Un ticket tel qu'il est posé en base : l'état et les instants qui vont avec. */
	private record TicketDemo(Statut statut, Origine origine, short priorite, String motif,
			Instant cree, Instant appele, Instant graceExpire, Instant consultation,
			Instant clos) {
	}

	/**
	 * Donne à la clinique de démonstration de quoi remplir ses écrans : une file
	 * du jour en cours, la veille avec deux départs, des rendez-vous à venir, des
	 * animaux avec leur carnet.
	 *
	 * <p><strong>Garde propre : rien n'est semé si la clinique a déjà des
	 * tickets.</strong> {@code run} ne passe ici qu'à la création de la clinique,
	 * mais le semis finira appelé d'ailleurs (une commande de réinitialisation de
	 * la démonstration, par exemple) ; sans cette garde, il doublerait la file.
	 * Elle est publique pour qu'un test l'exerce <em>directement</em> : repasser
	 * par {@code run} s'arrêterait au garde-fou du slug et ne la toucherait pas.
	 *
	 * <p>Toutes les dates se calculent depuis l'horloge injectée, en fuseau de la
	 * clinique : « hier » doit rester hier entre minuit et une heure à Paris, là
	 * où un calcul en UTC croirait encore à la veille.
	 *
	 * <p>Aucun numéro de téléphone nulle part : un numéro, même inventé, ferait
	 * partir de vrais SMS quand la grâce expire, que la file avance ou qu'un
	 * rappel arrive. Un test le garde.
	 *
	 * @param tenantId la clinique à garnir, déjà créée.
	 */
	public void semerLaDemonstration(UUID tenantId) {
		ContexteTenant.executer(tenantId, () -> {
			Long dejaLa = jdbc.queryForObject(
					"SELECT count(*) FROM ticket WHERE tenant_id = ?", Long.class, tenantId);
			if (dejaLa != null && dejaLa > 0) {
				JOURNAL.info("la clinique a déjà des tickets : démonstration non semée");
				return;
			}
			LocalDate aujourdhui = LocalDate.now(horloge);
			semerLaFileDuJour(tenantId, aujourdhui);
			semerLaVeille(tenantId, dernierJourOuvre(aujourdhui));
			semerLAgenda(tenantId, aujourdhui);
			semerLesCarnets(tenantId, aujourdhui);
		});
	}

	/** Les horaires que le semis pose dans l'agenda, et que la file du jour respecte. */
	private record Plage(LocalTime debut, LocalTime fin) {
	}

	private static final Plage MATIN = new Plage(LocalTime.of(9, 0), LocalTime.of(12, 0));

	private static final Plage APRES_MIDI = new Plage(LocalTime.of(14, 0), LocalTime.of(18, 0));

	/** Ce que couvre la file du jour : de la première arrivée à l'instant de référence. */
	private static final int DUREE_DE_LA_FILE_MINUTES = 95;

	private static final int DELAI_GRACE_PAR_DEFAUT_SECONDES = 180;

	/** Le samedi et le dimanche, le matin seulement. */
	private static List<Plage> plagesDu(DayOfWeek jour) {
		boolean semaine = jour != DayOfWeek.SATURDAY && jour != DayOfWeek.SUNDAY;
		return semaine ? List.of(MATIN, APRES_MIDI) : List.of(MATIN);
	}

	private Duration delaiGrace() {
		return Duration.ofSeconds(proprietes.delaiGraceSecondes() == null
				? DELAI_GRACE_PAR_DEFAUT_SECONDES : proprietes.delaiGraceSecondes());
	}

	/** Le dernier jour de semaine avant celui-ci : un lundi, c'est le vendredi. */
	private static LocalDate dernierJourOuvre(LocalDate jour) {
		LocalDate veille = jour.minusDays(1);
		while (veille.getDayOfWeek() == DayOfWeek.SATURDAY
				|| veille.getDayOfWeek() == DayOfWeek.SUNDAY) {
			veille = veille.minusDays(1);
		}
		return veille;
	}

	/**
	 * L'instant que la file du jour prend pour « maintenant ».
	 *
	 * <p>La file ne doit pas montrer d'arrivée avant l'ouverture ni de
	 * consultation pendant la pause : installée à 8 h 30, elle serait en retard
	 * sur l'ouverture ; à 13 h, ses consultations tomberaient à midi. Pendant
	 * les horaires, l'instant est l'heure réelle, repoussée si besoin à
	 * l'ouverture + la durée de la file. Hors horaires, c'est la fin de la
	 * dernière plage passée (la file est alors celle d'un moment révolu), ou, avant
	 * l'ouverture, un moment de la première plage — daté un peu dans le futur.
	 */
	private Instant momentDeLaFile(LocalDate jour) {
		java.time.LocalDateTime maintenant = java.time.LocalDateTime.ofInstant(horloge.instant(),
				horloge.getZone());
		LocalTime heure = maintenant.toLocalTime();
		List<Plage> plages = plagesDu(jour.getDayOfWeek());
		LocalTime reference = plages.get(0).debut().plusMinutes(DUREE_DE_LA_FILE_MINUTES);
		for (Plage plage : plages) {
			if (!heure.isBefore(plage.debut()) && heure.isBefore(plage.fin())) {
				LocalTime plancher = plage.debut().plusMinutes(DUREE_DE_LA_FILE_MINUTES);
				reference = heure.isBefore(plancher) ? plancher : heure;
			}
			else if (!heure.isBefore(plage.fin())) {
				reference = plage.fin();
			}
		}
		return jour.atTime(reference).atZone(horloge.getZone()).toInstant();
	}

	/**
	 * Une matinée de consultation en cours, vue de l'accueil : quatre passages
	 * terminés d'un quart d'heure environ, une consultation commencée, puis la
	 * file, dans l'état que la machine à états produirait elle-même.
	 *
	 * <p>Chaque passage a été appelé avant d'entrer en consultation, comme le
	 * produit le veut : sans {@code appele_le}, le tableau de bord ne verrait
	 * qu'un seul retard. Une seule salle : les consultations se suivent.
	 *
	 * <p>Le seuil de prévenance de la clinique est de deux : les deux premiers de
	 * la file sont donc l'appelé et le prévenu, les suivants attendent. Un
	 * {@code EN_ATTENTE} placé sous le seuil serait un état que le produit ne
	 * peut pas atteindre, et un vétérinaire le verrait. La priorité est graduée :
	 * le chat en caisse (rang 7) passe avant la personne âgée (rang 5), qui passe
	 * avant la file normale. Le rang 7 est celui qu'on appelle.
	 */
	private void semerLaFileDuJour(UUID tenantId, LocalDate jour) {
		Instant reference = momentDeLaFile(jour);
		java.util.function.IntFunction<Instant> ilYa =
				minutes -> reference.minus(Duration.ofMinutes(minutes));

		List<TicketDemo> file = List.of(
				passage(Origine.COMPTOIR, ilYa.apply(95), ilYa.apply(77), ilYa.apply(75),
						ilYa.apply(60)),
				passage(Origine.ACCUEIL, ilYa.apply(80), ilYa.apply(62), ilYa.apply(60),
						ilYa.apply(44)),
				passage(Origine.COMPTOIR, ilYa.apply(66), ilYa.apply(46), ilYa.apply(44),
						ilYa.apply(29)),
				passage(Origine.COMPTOIR, ilYa.apply(50), ilYa.apply(31), ilYa.apply(29),
						ilYa.apply(12)),
				// En consultation : appelé, donc plus de grâce à courir (Ticket.present()).
				new TicketDemo(Statut.EN_CONSULTATION, Origine.COMPTOIR, Priorite.NORMALE, null,
						ilYa.apply(30), ilYa.apply(14), null, ilYa.apply(12), null),
				enFile(Statut.EN_ATTENTE, Origine.COMPTOIR, ilYa.apply(15)),
				// Les deux passages prioritaires arrivent APRÈS l'appel du ticket
				// précédent : appelé avant eux alors qu'ils attendaient, il leur
				// aurait doublé la file, ce que la priorité interdit.
				new TicketDemo(Statut.APPELE, Origine.ACCUEIL, RANG_CHAT_EN_CAISSE,
						MOTIF_CHAT_EN_CAISSE, ilYa.apply(13), ilYa.apply(1),
						ilYa.apply(1).plus(delaiGrace()), null, null),
				new TicketDemo(Statut.PREVENU, Origine.ACCUEIL, Priorite.PRIORITAIRE,
						MotifDao.DEFAUTS.get(0), ilYa.apply(11), null, null, null, null),
				enFile(Statut.EN_ATTENTE, Origine.COMPTOIR, ilYa.apply(6)));
		poserLesTickets(tenantId, jour, file);
	}

	/**
	 * La veille : huit consultations, deux renoncements. C'est ce qui donne ses
	 * chiffres à la vue Mesure — départs, part des tickets pris au comptoir, et
	 * un nombre d'arrivants saisi par l'accueil pour que le taux existe.
	 *
	 * <p>Chaque renoncement a lieu pendant une consultation longue (28 min) qui
	 * occupe la salle : personne ne renonce devant une salle vide. Huit tickets
	 * sur dix viennent du comptoir, pour treize arrivants : un taux de 62 %,
	 * franchement au-dessus du seuil de 50 %, pas en zone grise.
	 */
	private void semerLaVeille(UUID tenantId, LocalDate veille) {
		List<TicketDemo> tickets = List.of(
				passageLe(veille, Origine.COMPTOIR, "09:08", "09:22", "09:24", "09:41"),
				passageLe(veille, Origine.ACCUEIL, "09:26", "09:40", "09:42", "10:00"),
				passageLe(veille, Origine.COMPTOIR, "09:35", "09:58", "10:00", "10:16"),
				passageLe(veille, Origine.COMPTOIR, "10:05", "10:14", "10:16", "10:44"),
				partiLe(veille, Origine.COMPTOIR, "10:12", "10:38"),
				passageLe(veille, Origine.ACCUEIL, "11:05", "11:18", "11:20", "11:37"),
				passageLe(veille, Origine.COMPTOIR, "14:10", "14:22", "14:24", "14:40"),
				passageLe(veille, Origine.COMPTOIR, "14:25", "14:38", "14:40", "15:08"),
				partiLe(veille, Origine.COMPTOIR, "14:32", "14:58"),
				passageLe(veille, Origine.COMPTOIR, "16:15", "16:27", "16:29", "16:46"));
		poserLesTickets(tenantId, veille, tickets);
		jdbc.update("INSERT INTO mesure_jour (tenant_id, jour, arrivants_total) VALUES (?, ?, ?)",
				tenantId, veille, ARRIVANTS_DE_LA_VEILLE);
	}

	private static final int ARRIVANTS_DE_LA_VEILLE = 13;

	/** Un passage terminé : créé, appelé, en consultation, sorti. */
	private TicketDemo passage(Origine origine, Instant cree, Instant appele,
			Instant debutConsultation, Instant fin) {
		return new TicketDemo(Statut.TERMINE, origine, Priorite.NORMALE, null, cree, appele, null,
				debutConsultation, fin);
	}

	private static TicketDemo enFile(Statut statut, Origine origine, Instant cree) {
		return new TicketDemo(statut, origine, Priorite.NORMALE, null, cree, null, null,
				null, null);
	}

	private TicketDemo passageLe(LocalDate jour, Origine origine, String cree, String appele,
			String debut, String fin) {
		return passage(origine, ce(jour, cree), ce(jour, appele), ce(jour, debut), ce(jour, fin));
	}

	private TicketDemo partiLe(LocalDate jour, Origine origine, String cree, String parti) {
		return new TicketDemo(Statut.PARTI, origine, Priorite.NORMALE, null, ce(jour, cree), null,
				null, null, ce(jour, parti));
	}

	private Instant ce(LocalDate jour, String heure) {
		return jour.atTime(LocalTime.parse(heure)).atZone(horloge.getZone()).toInstant();
	}

	/** Numérote dans l'ordre de la liste, qui est l'ordre d'arrivée. */
	private void poserLesTickets(UUID tenantId, LocalDate jour, List<TicketDemo> tickets) {
		int numero = 0;
		for (TicketDemo t : tickets) {
			numero++;
			jdbc.update("""
					INSERT INTO ticket (id, tenant_id, jour, numero_jour, reference,
					                    telephone_e164, telephone_valide, priorite, motif_priorite,
					                    ordre, statut, origine, cree_le, appele_le,
					                    grace_expire_le, consultation_le, clos_le)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", UUID.randomUUID(), tenantId, jour, numero, jetons.suivante(),
					null, null, t.priorite(), t.motif(), t.cree().toEpochMilli() * Ticket.ECHELLE_ORDRE + numero,
					t.statut().name(), t.origine().name(), horodatage(t.cree()),
					horodatage(t.appele()), horodatage(t.graceExpire()),
					horodatage(t.consultation()), horodatage(t.clos()));
		}
	}

	private static Timestamp horodatage(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	/**
	 * Des horaires de clinique de ville, et trois rendez-vous des deux prochains
	 * jours ouvrés, tous le matin : un créneau d'après-midi tomberait un samedi
	 * hors des plages.
	 */
	private void semerLAgenda(UUID tenantId, LocalDate aujourdhui) {
		for (DayOfWeek jour : DayOfWeek.values()) {
			if (jour == DayOfWeek.SUNDAY) {
				continue;
			}
			for (Plage plage : plagesDu(jour)) {
				agenda.ajouterPlage(tenantId, jour, plage.debut(), plage.fin(), (short) 30,
						(short) 1);
			}
		}
		LocalDate demain = prochainJourOuvre(aujourdhui);
		LocalDate apresDemain = prochainJourOuvre(demain);
		poserUnRendezVous(tenantId, demain, LocalTime.of(9, 30), "Rappel de vaccin annuel");
		poserUnRendezVous(tenantId, demain, LocalTime.of(11, 0),
				"Contrôle post-opératoire : retrait des fils");
		poserUnRendezVous(tenantId, apresDemain, LocalTime.of(10, 30),
				"Boiterie depuis trois jours");
	}

	private static LocalDate prochainJourOuvre(LocalDate jour) {
		LocalDate suivant = jour.plusDays(1);
		return suivant.getDayOfWeek() == DayOfWeek.SUNDAY ? suivant.plusDays(1) : suivant;
	}

	/**
	 * Sans téléphone : le rappel de la veille ne part que pour un rendez-vous qui
	 * en a un, et une démonstration ne doit envoyer aucun SMS.
	 */
	private void poserUnRendezVous(UUID tenantId, LocalDate jour, LocalTime heure,
			String motif) {
		agenda.materialiser(tenantId, jour, heure, (short) 1);
		UUID creneau = agenda.reserverUnePlace(tenantId, jour, heure).orElseThrow(
				() -> new IllegalStateException("créneau de démonstration plein : " + jour));
		agenda.poser(tenantId, UUID.randomUUID(), creneau, jour, heure, jetons.suivante(), null,
				motif);
	}

	/**
	 * Deux animaux dont le carnet est ouvert (un QR émis), avec des rappels de
	 * vaccin à venir. Sans téléphone, pour la même raison que les rendez-vous.
	 */
	private void semerLesCarnets(UUID tenantId, LocalDate aujourdhui) {
		Instant maintenant = horloge.instant();
		UUID nougat = carnets.creer(tenantId, "Nougat", "Chat", null, jetons.suivante(),
				maintenant);
		// Faits il y a onze mois et demi : l'échéance annuelle tombe dans quinze jours,
		// donc dans la fenêtre des rappels (ServiceCarnet.JOURS_AVANT_RAPPEL). Un carnet
		// sans rappel proche ne montrerait pas ce à quoi il sert.
		LocalDate dernierRappelChat = aujourdhui.minusMonths(12).plusDays(15);
		carnets.ajouterVaccination(tenantId, nougat, "Typhus - coryza (TC)", dernierRappelChat,
				dernierRappelChat.plusYears(1), null);
		carnets.ajouterVaccination(tenantId, nougat, "Leucose féline", dernierRappelChat,
				dernierRappelChat.plusYears(1), null);
		carnets.ajouterVaccination(tenantId, nougat, "Rage", aujourdhui.minusYears(2),
				aujourdhui.plusYears(1), "Vaccin antirabique triennal : rappel tous les trois ans");

		UUID ulysse = carnets.creer(tenantId, "Ulysse", "Chien", null, jetons.suivante(),
				maintenant);
		LocalDate dernierRappelChien = aujourdhui.minusMonths(10);
		carnets.ajouterVaccination(tenantId, ulysse, "CHPPi (carré, hépatite, parvovirose, parainfluenza)",
				dernierRappelChien, dernierRappelChien.plusYears(1), null);
		carnets.ajouterVaccination(tenantId, ulysse, "Leptospirose", dernierRappelChien,
				dernierRappelChien.plusYears(1), null);
	}

	@Override
	public void run(ApplicationArguments arguments) {
		if (proprietes.slug() == null || proprietes.motDePasse() == null
				|| proprietes.motDePasse().isBlank()) {
			JOURNAL.warn("amorçage demandé sans slug ni mot de passe : rien à faire");
			return;
		}
		if (tenants.findBySlugAndActifTrue(proprietes.slug()).isPresent()) {
			JOURNAL.info("clinique « {} » déjà installée", proprietes.slug());
			return;
		}

		String expediteur = proprietes.expediteurSms();
		if (expediteur != null && !NomExpediteur.valide(expediteur)) {
			throw new IllegalArgumentException(("expéditeur SMS « %s » non conforme : 3 à 11 "
					+ "caractères alphanumériques sans accent. Proposition : « %s »")
					.formatted(expediteur, NomExpediteur.proposerDepuis(proprietes.nom())));
		}

		UUID tenantId = UUID.randomUUID();
		// Pas d'écran demandé, pas de jeton : la clinique n'en a tout
		// simplement pas, et ses routes d'écran répondront 404 (EF-603).
		String jetonEcran = proprietes.avecEcran() ? jetons.suivante() : null;
		jdbc.update("""
				INSERT INTO tenant (id, slug, nom, fuseau, delai_grace_secondes,
				                    seuil_prevenir, actif, expediteur_sms, jeton_ecran)
				VALUES (?, ?, ?, 'Europe/Paris', ?, ?, true, ?, ?)
				""", tenantId, proprietes.slug(), proprietes.nom(),
				(int) delaiGrace().toSeconds(),
				proprietes.seuilPrevenir() == null ? 2 : proprietes.seuilPrevenir(), expediteur,
				jetonEcran);

		// La table « utilisateur » EST soumise au RLS : sans tenant lié, la
		// politique refuserait l'insertion (WITH CHECK compare à NULL). Il faut
		// donc lier le tenant qu'on vient de créer. C'est la preuve que la
		// seconde barrière fonctionne, y compris contre notre propre code.
		ContexteTenant.executer(tenantId, () -> jdbc.update("""
				INSERT INTO utilisateur (id, tenant_id, email, mdp_hash, role, actif)
				VALUES (?, ?, ?, ?, ?, true)
				""", UUID.randomUUID(), tenantId, proprietes.email().trim().toLowerCase(),
				motsDePasse.encode(proprietes.motDePasse()), Role.GERANT.name()));

		// Les deux motifs de priorité du cahier des charges (EF-407). La
		// clinique les renomme, les désactive ou en ajoute ensuite.
		ContexteTenant.executer(tenantId, () -> {
			motifs.poserLesDefauts(tenantId);
			poserLesMotifsDeDemonstration(tenantId);
		});

		if (proprietes.active()) {
			semerLaDemonstration(tenantId);
		}

		JOURNAL.info("clinique « {} » installée — comptoir : /t/{}",
				proprietes.nom(), proprietes.slug());
		if (jetonEcran != null) {
			// Journalisé une fois, à l'installation : c'est l'URL qu'on colle
			// dans le navigateur du téléviseur. Elle n'est plus jamais
			// affichée ailleurs, et la retrouver demande un accès à la base.
			JOURNAL.info("écran de salle d'attente appairé — /ecran/{}", jetonEcran);
		}
		else {
			JOURNAL.info("clinique « {} » sans écran de salle d'attente : aucune route "
					+ "d'écran ne répondra pour elle", proprietes.slug());
		}
	}
}

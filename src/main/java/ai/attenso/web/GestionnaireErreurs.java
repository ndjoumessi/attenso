package ai.attenso.web;

import ai.attenso.tenant.TenantInconnu;
import ai.attenso.ticket.LimiteNumeroAtteinte;
import ai.attenso.ticket.NumeroInjoignable;
import ai.attenso.ticket.NumeroInvalide;
import ai.attenso.ticket.PlafondJournalierAtteint;
import ai.attenso.ticket.TicketInconnu;
import ai.attenso.ticket.TransitionInterdite;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduction des erreurs du domaine en réponses HTTP.
 *
 * <p>Les messages sont destinés à être lus par l'ASV sur une tablette, pas par
 * un développeur : « ce ticket a déjà été traité » plutôt qu'une trace. Rien
 * de ce qui est renvoyé ne révèle l'état interne au-delà de ce que
 * l'utilisateur voit déjà à l'écran.
 */
@RestControllerAdvice
public class GestionnaireErreurs {

	@ExceptionHandler(TicketInconnu.class)
	ProblemDetail ticketInconnu(TicketInconnu erreur) {
		return probleme(HttpStatus.NOT_FOUND, "Ticket introuvable",
				"Ce ticket n'existe pas, ou la journée est terminée.");
	}

	@ExceptionHandler(CarnetInconnu.class)
	ProblemDetail carnetInconnu(CarnetInconnu erreur) {
		return probleme(HttpStatus.NOT_FOUND, "Carnet introuvable",
				"Ce QR code ne donne accès à aucun carnet.");
	}

	@ExceptionHandler(RendezVousInconnu.class)
	ProblemDetail rendezVousInconnu(RendezVousInconnu erreur) {
		// Même réponse qu'un rendez-vous annulé ou passé : distinguer les cas
		// donnerait un oracle à qui essaie des références au hasard.
		return probleme(HttpStatus.NOT_FOUND, "Rendez-vous introuvable",
				"Ce lien ne correspond à aucun rendez-vous.");
	}

	@ExceptionHandler(ai.attenso.agenda.CreneauComplet.class)
	ProblemDetail creneauComplet(ai.attenso.agenda.CreneauComplet erreur) {
		// 409 et non 400 : la demande était valable, c'est l'état du monde qui
		// a changé entre l'affichage et le clic. Le message invite donc à
		// choisir une autre heure, pas à corriger une saisie.
		return probleme(HttpStatus.CONFLICT, "Créneau complet",
				"Ce créneau vient d'être pris. Choisissez une autre heure.");
	}

	@ExceptionHandler(ai.attenso.agenda.CreneauPasse.class)
	ProblemDetail creneauPasse(ai.attenso.agenda.CreneauPasse erreur) {
		// 409 comme le créneau complet, et pour la même raison : la demande
		// était formée correctement, c'est l'heure qui a tourné. Le message
		// invite à recharger, puisque la page affichée est périmée.
		return probleme(HttpStatus.CONFLICT, "Créneau passé",
				"Cette heure est déjà passée. Rechargez la page pour voir les "
						+ "créneaux encore libres.");
	}

	@ExceptionHandler(ai.attenso.paiement.SouscriptionIndisponible.class)
	ProblemDetail souscriptionIndisponible(
			ai.attenso.paiement.SouscriptionIndisponible erreur) {
		// 503 et non 500 : la demande était valable, c'est notre prestataire de
		// paiement qui manque à l'appel. Sans ce gestionnaire, l'exception
		// repartait en 500, le renvoi vers /error repassait par la chaîne de
		// sécurité, et la page affichait « Erreur 401 » — un message qui
		// envoie chercher un problème de connexion là où il n'y en a pas.
		return probleme(HttpStatus.SERVICE_UNAVAILABLE, "Souscription indisponible",
				"L'inscription en ligne est momentanément fermée. Écrivez-nous à "
						+ "contact@attenso.fr, nous ouvrons votre clinique à la main.");
	}

	@ExceptionHandler(ai.attenso.inscription.PalierInconnu.class)
	ProblemDetail palierInconnu(ai.attenso.inscription.PalierInconnu erreur) {
		return probleme(HttpStatus.BAD_REQUEST, "Formule inconnue",
				"Choisissez l'une des formules proposées.");
	}

	@ExceptionHandler(TenantInconnu.class)
	ProblemDetail cliniqueInconnue(TenantInconnu erreur) {
		return probleme(HttpStatus.NOT_FOUND, "Clinique introuvable",
				"Ce lien ne correspond à aucune clinique active.");
	}

	@ExceptionHandler(TransitionInterdite.class)
	ProblemDetail transitionInterdite(TransitionInterdite erreur) {
		ProblemDetail detail = probleme(HttpStatus.CONFLICT, "Action impossible",
				"Ce ticket est déjà passé à « %s » : rechargez le tableau."
						.formatted(erreur.depuis()));
		detail.setProperty("statutCourant", erreur.depuis().name());
		return detail;
	}

	@ExceptionHandler(NumeroInvalide.class)
	ProblemDetail numeroInvalide(NumeroInvalide erreur) {
		return probleme(HttpStatus.BAD_REQUEST, "Numéro invalide",
				"Entrez un numéro de mobile français, par exemple 06 12 34 56 78.");
	}

	@ExceptionHandler(LimiteNumeroAtteinte.class)
	ProblemDetail limiteNumero(LimiteNumeroAtteinte erreur) {
		return probleme(HttpStatus.TOO_MANY_REQUESTS, "Déjà enregistré",
				"Ce numéro a déjà ses tickets pour aujourd'hui. Voyez l'accueil.");
	}

	@ExceptionHandler(NumeroInjoignable.class)
	ProblemDetail numeroInjoignable(NumeroInjoignable erreur) {
		return probleme(HttpStatus.BAD_REQUEST, "Numéro injoignable",
				"Ce numéro ne reçoit pas de SMS. Vérifiez-le, ou voyez l'accueil.");
	}

	@ExceptionHandler(PlafondJournalierAtteint.class)
	ProblemDetail plafondJournalier(PlafondJournalierAtteint erreur) {
		return probleme(HttpStatus.TOO_MANY_REQUESTS, "Enregistrement indisponible",
				"Le nombre de tickets du jour a atteint sa limite. "
						+ "Adressez-vous à l'accueil.");
	}

	@ExceptionHandler(TropDeRequetes.class)
	ProblemDetail tropDeRequetes(TropDeRequetes erreur) {
		return probleme(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives",
				"Patientez quelques instants avant de réessayer.");
	}

	@ExceptionHandler(IdentifiantsRefuses.class)
	ProblemDetail identifiantsRefuses(IdentifiantsRefuses erreur) {
		return probleme(HttpStatus.UNAUTHORIZED, "Connexion refusée",
				"Adresse ou mot de passe incorrect.");
	}

	private ProblemDetail probleme(HttpStatus statut, String titre, String detail) {
		ProblemDetail probleme = ProblemDetail.forStatus(statut);
		probleme.setTitle(titre);
		probleme.setDetail(detail);
		return probleme;
	}
}

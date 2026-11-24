package ai.attenso.config;

import ai.attenso.securite.FiltreJetonPersonnel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class ConfigurationSecurite {

	@Bean
	SecurityFilterChain chaine(HttpSecurity http, FiltreJetonPersonnel filtre) throws Exception {
		return http
				// API sans état, jeton porté par l'en-tête : pas de cookie de
				// session, donc rien que le CSRF puisse protéger.
				.csrf(csrf -> csrf.disable())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(acces -> acces
						// Parcours client : la référence du lien tient lieu de
						// justificatif, limité en débit par IP.
						.requestMatchers("/", "/index.html", "/accueil", "/s/**", "/t/**",
								"/ecran/**", "/manifest.webmanifest", "/sw.js", "/app/**",
								"/favicon.ico")
						.permitAll()
						.requestMatchers(HttpMethod.POST, "/api/public/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/suivi/**", "/api/ecran/**").permitAll()
						// EF-205 : le client signale son approche. La référence du
						// lien vaut justificatif, comme pour la consultation —
						// c'est le même objet, avec la même portée d'une journée.
						.requestMatchers(HttpMethod.POST, "/api/suivi/*/jarrive").permitAll()
						// EF-307 : l'abonnement push, même justificatif que le reste
						// de la page de suivi — la référence du lien.
						.requestMatchers(HttpMethod.POST, "/api/suivi/*/push").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/push/cle").permitAll()
						// EF-901 : inscription autonome. Point d'entrée anonyme qui
						// CRÉE des comptes : la limite de débit du contrôleur est
						// serrée, et c'est elle qui porte la protection.
						.requestMatchers(HttpMethod.POST, "/api/inscription").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/inscription/paliers").permitAll()
						// Les pages légales et l'identité de l'éditeur : publier ces
						// informations est précisément ce que la loi impose.
						.requestMatchers("/mentions-legales", "/cgv", "/confidentialite")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/editeur").permitAll()
						.requestMatchers("/inscription").permitAll()
						// EF-101 : la page de l'affiche. Du balisage inerte — les
						// données viennent de /api/affiche, qui exige le jeton. La
						// servir derrière l'authentification demanderait au
						// navigateur de poser un en-tête sur une navigation, ce
						// qu'il ne sait pas faire.
						.requestMatchers("/affiche").permitAll()
						// EF-905 : la réservation en ligne est publique par
						// construction — c'est un client qui n'a pas de compte.
						// La référence opaque du rendez-vous vaut ensuite
						// justificatif, comme celle d'un ticket.
						.requestMatchers(HttpMethod.GET, "/api/rendez-vous/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/rendez-vous/*/annuler")
						.permitAll()
						.requestMatchers("/rdv/**", "/r/**").permitAll()
						// EF-906 : le QR du carnet est collé sur une médaille. Le
						// jeton vaut justificatif, et il est révocable.
						.requestMatchers(HttpMethod.GET, "/api/carnet/**").permitAll()
						.requestMatchers("/carnet/**").permitAll()
						// Le webhook de Stripe. Publique par nécessité — Stripe ne
						// porte aucun jeton de notre part — et authentifiée par la
						// SIGNATURE du corps, vérifiée dans ControleurPaiement. C'est
						// la seule route de ce produit dont la sécurité ne tient pas à
						// un filtre mais au contrôleur lui-même.
						.requestMatchers(HttpMethod.POST, "/api/paiement/webhook").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/connexion").permitAll()
						// Récupération de mot de passe : publique par nature — celui qui
						// l'utilise ne peut justement pas se connecter. La protection
						// tient au jeton envoyé par courriel et aux limites de débit.
						.requestMatchers(HttpMethod.POST, "/api/connexion/oubli",
								"/api/connexion/reinitialiser").permitAll()
						.requestMatchers("/reinitialisation/**").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						// Ce qui relève de l'ACHETEUR et non de l'utilisatrice : les
						// comptes, l'abonnement, et l'export complet. C'est le premier
						// endroit où les deux rôles cessent d'être décoratifs — jusqu'ici
						// ils ouvraient exactement les mêmes portes.
						//
						// L'export mérite d'y être autant que la facturation : il emporte
						// TOUT, numéros compris, dans un fichier qui sort de la clinique.
						// C'est la porte de sortie du client, et elle n'a pas à être à
						// portée de clic de quiconque tient le comptoir un après-midi.
						//
						// Le tableau de bord et la supervision restent ouverts aux deux :
						// ce sont des chiffres du jour, utiles à qui fait avancer la file.
						.requestMatchers("/api/equipe/**", "/api/facturation/**",
								"/api/export/**").hasRole("GERANT")
						// Tout le reste est le back-office de la clinique.
						.anyRequest().hasAnyRole("ACCUEIL", "GERANT"))
				// Sans point d'entrée déclaré, une requête sans jeton reçoit 403.
				// C'est le mauvais signal : la tablette doit distinguer « il
				// faut se reconnecter » (401) de « ce rôle n'a pas le droit »
				// (403), sinon elle ne sait pas quoi faire d'un échec.
				.exceptionHandling(erreurs -> erreurs.authenticationEntryPoint(
						new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.addFilterBefore(filtre, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	PasswordEncoder encodeurMotDePasse() {
		return new BCryptPasswordEncoder();
	}
}

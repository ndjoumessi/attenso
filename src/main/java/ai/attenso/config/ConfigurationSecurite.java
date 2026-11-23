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
						.requestMatchers(HttpMethod.POST, "/api/connexion").permitAll()
						.requestMatchers("/actuator/health").permitAll()
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

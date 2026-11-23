package ai.attenso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param derriereProxy ne faire confiance à {@code X-Forwarded-For} que si
 *        c'est vrai. À faux, un client pourrait forger l'en-tête et se donner
 *        autant de quotas qu'il veut.
 * @param relaisDeConfiance nombre de relais placés devant l'application. Sert
 *        à compter depuis la droite de l'en-tête, seul côté que le client ne
 *        contrôle pas. Un seul relais en V0.
 */
@ConfigurationProperties("attenso.reseau")
public record ProprietesReseau(boolean derriereProxy, int relaisDeConfiance) {
}

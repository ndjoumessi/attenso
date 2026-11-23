package ai.attenso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param derriereProxy ne faire confiance à {@code X-Forwarded-For} que si
 *        c'est vrai. À faux, un client pourrait forger l'en-tête et se donner
 *        autant de quotas qu'il veut.
 */
@ConfigurationProperties("attenso.reseau")
public record ProprietesReseau(boolean derriereProxy) {
}

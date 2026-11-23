package ai.attenso.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class ConfigurationJeton {

	private static final MacAlgorithm ALGORITHME = MacAlgorithm.HS256;

	private final SecretKeySpec cle;

	ConfigurationJeton(ProprietesJeton proprietes) {
		this.cle = new SecretKeySpec(proprietes.secret().getBytes(), "HmacSHA256");
	}

	@Bean
	JwtEncoder encodeurJeton() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(cle));
	}

	@Bean
	JwtDecoder decodeurJeton() {
		return NimbusJwtDecoder.withSecretKey(cle).macAlgorithm(ALGORITHME).build();
	}
}

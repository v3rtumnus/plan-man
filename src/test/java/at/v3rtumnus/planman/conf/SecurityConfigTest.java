package at.v3rtumnus.planman.conf;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void passwordEncoder_returnsBCryptPasswordEncoder() {
        PasswordEncoder encoder = config.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(encoder.matches("secret", encoder.encode("secret"))).isTrue();
    }

    @Test
    void persistentTokenRepository_setsDataSource() {
        DataSource dataSource = mock(DataSource.class);
        ReflectionTestUtils.setField(config, "dataSource", dataSource);

        PersistentTokenRepository repo = config.persistentTokenRepository();

        assertThat(repo).isInstanceOf(JdbcTokenRepositoryImpl.class);
    }

    private static MockHttpServletRequest request(String remoteUser, String proxyAuth) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (remoteUser != null) request.addHeader(SecurityConfig.REMOTE_USER_HEADER, remoteUser);
        if (proxyAuth != null) request.addHeader(SecurityConfig.PROXY_AUTH_HEADER, proxyAuth);
        return request;
    }

    @Test
    void trustedProxyRequest_matchesRemoteUserWithCorrectSecret() {
        RequestMatcher matcher = SecurityConfig.trustedProxyRequest("s3cret");

        assertThat(matcher.matches(request("michael", "s3cret"))).isTrue();
    }

    @Test
    void trustedProxyRequest_rejectsRemoteUserWithoutOrWithWrongSecret() {
        RequestMatcher matcher = SecurityConfig.trustedProxyRequest("s3cret");

        assertThat(matcher.matches(request("michael", null))).isFalse();
        assertThat(matcher.matches(request("michael", "wrong"))).isFalse();
        assertThat(matcher.matches(request("michael", "s3cret-longer"))).isFalse();
        assertThat(matcher.matches(request(null, "s3cret"))).isFalse();
    }

    @Test
    void trustedProxyRequest_matchesNothingWhenSecretUnset() {
        assertThat(SecurityConfig.trustedProxyRequest("").matches(request("michael", ""))).isFalse();
        assertThat(SecurityConfig.trustedProxyRequest(null).matches(request("michael", "x"))).isFalse();
    }
}

package at.v3rtumnus.planman.conf;

import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class SecurityConfig {

    static final String REMOTE_USER_HEADER = "Remote-User";
    // Set by Traefik after the Authelia forward-auth, so a Remote-User header sent
    // straight to the container from inside dockernet is not trusted.
    static final String PROXY_AUTH_HEADER = "X-Plan-Man-Proxy-Auth";

    @Value("${planman.security.proxy-secret:}")
    private String proxySecret;

    @Autowired
    private AccessDeniedHandler accessDeniedHandler;

    @Autowired
    private PlanManUserDetailsService userDetailsService;

    @Autowired
    private DataSource dataSource;

    @Bean
    @Order(1)
    public SecurityFilterChain autheliaFilterChain(HttpSecurity http) throws Exception {
        PreAuthenticatedAuthenticationProvider preAuthProvider = new PreAuthenticatedAuthenticationProvider();
        preAuthProvider.setPreAuthenticatedUserDetailsService(
                new UserDetailsByNameServiceWrapper<>(userDetailsService));

        RequestHeaderAuthenticationFilter requestHeaderFilter = new RequestHeaderAuthenticationFilter();
        requestHeaderFilter.setPrincipalRequestHeader(REMOTE_USER_HEADER);
        requestHeaderFilter.setExceptionIfHeaderMissing(false);
        requestHeaderFilter.setAuthenticationManager(new ProviderManager(preAuthProvider));

        http
                .securityMatcher(trustedProxyRequest(proxySecret))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .addFilterBefore(requestHeaderFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

        return http.build();
    }

    @Bean
    @Order(2)
    protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("frame-src https://www.youtube.com; frame-ancestors 'self' home.altenburger.io")))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/resources/**", "/favicon.ico", "/actuator/**").permitAll()
                        .requestMatchers("/credit/**").hasAnyRole("USER")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .permitAll())
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler))
                .rememberMe(remember -> remember
                        .tokenRepository(persistentTokenRepository())
                        .rememberMeParameter("remember-me")
                        .rememberMeCookieName("plan-man-remember-me"));

        return http.build();
    }

    /**
     * Matches only requests that carry Remote-User and the proxy secret. An empty
     * secret matches nothing, so header login stays off until it is configured.
     */
    static RequestMatcher trustedProxyRequest(String secret) {
        byte[] expected = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        return request -> {
            if (expected.length == 0 || request.getHeader(REMOTE_USER_HEADER) == null) {
                return false;
            }
            String presented = request.getHeader(PROXY_AUTH_HEADER);
            return presented != null
                    && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
        };
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl tokenRepository = new JdbcTokenRepositoryImpl();
        tokenRepository.setDataSource(dataSource);
        return tokenRepository;
    }
}
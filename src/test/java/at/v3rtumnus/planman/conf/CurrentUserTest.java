package at.v3rtumnus.planman.conf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loggedInUser() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "michael", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        assertThat(CurrentUser.name()).isEqualTo("michael");
        assertThat(CurrentUser.require()).isEqualTo("michael");
    }

    @Test
    void nobodyOrAnonymous() {
        assertThat(CurrentUser.name()).isNull();
        assertThatThrownBy(CurrentUser::require).isInstanceOf(IllegalStateException.class);

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(CurrentUser.name()).isNull();
    }
}

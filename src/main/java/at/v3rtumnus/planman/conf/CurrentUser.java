package at.v3rtumnus.planman.conf;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The logged-in user's name (Authelia's Remote-User or the form login). */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The username, or null when nobody is logged in. */
    public static String name() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    /** The username; fails when nobody is logged in. */
    public static String require() {
        String name = name();
        if (name == null) {
            throw new IllegalStateException("No logged-in user");
        }
        return name;
    }
}

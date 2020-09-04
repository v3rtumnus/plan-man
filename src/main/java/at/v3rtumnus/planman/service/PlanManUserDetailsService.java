package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dao.UserProfileRepository;
import at.v3rtumnus.planman.dto.PlanManUser;
import at.v3rtumnus.planman.entity.UserProfile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class PlanManUserDetailsService implements UserDetailsService {

    private final UserProfileRepository userProfileRepository;

    public PlanManUserDetailsService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        UserProfile user = userProfileRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Username could not be found"));

        return new PlanManUser(user.getUsername(), user.getPassword(), Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
    }
}

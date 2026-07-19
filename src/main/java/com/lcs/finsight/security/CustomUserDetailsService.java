package com.lcs.finsight.security;

import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Throw Spring Security's own UsernameNotFoundException (not a custom one) so that
        // DaoAuthenticationProvider's hideUserNotFoundExceptions (on by default) converts it to a
        // BadCredentialsException — identical 401 to a wrong password. A custom exception would
        // instead be wrapped in InternalAuthenticationServiceException (HTTP 500) and, by responding
        // differently, would let a caller enumerate which emails are registered.
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found for email " + email + "."));

        return new CustomUserDetails(user);
    }
}

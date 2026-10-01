package com.smartwatch.user.security;

import com.smartwatch.user.entity.User;
import com.smartwatch.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserAccountDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public UserAccountDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UUID userId;
        try {
            userId = UUID.fromString(username);
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException("User not found");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new UserPrincipal(user.getId(), user.getEmail(), user.isEnabled());
    }
}

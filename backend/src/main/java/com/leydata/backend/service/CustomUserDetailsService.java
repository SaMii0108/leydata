package com.leydata.backend.service;

import com.leydata.backend.entity.Users;
import com.leydata.backend.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

        private final UsersRepository usersRepository;

        @Override
        @Transactional
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                Users user = usersRepository.findByEmail(username)
                                .orElseThrow(() -> new UsernameNotFoundException(
                                                "User not found with email: " + username));

                List<SimpleGrantedAuthority> authorities = user.getUserRoles() == null ? List.of()
                                : user.getUserRoles().stream()
                                                .map(usersRole -> new SimpleGrantedAuthority(
                                                                "ROLE_" + usersRole.getRole().getCode()))
                                                .collect(Collectors.toList());

                return User.builder()
                                .username(user.getEmail())
                                .password(user.getPassword())
                                .authorities(authorities)
                                .accountExpired(false)
                                .accountLocked(false)
                                .credentialsExpired(false)
                                .disabled(!user.getActive())
                                .build();
        }
}

package com.leydata.backend.service;

import com.leydata.backend.dto.AuthRequest;
import com.leydata.backend.dto.AuthResponse;
import com.leydata.backend.entity.Users;
import com.leydata.backend.repository.UsersRepository;
import com.leydata.backend.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

        private final AuthenticationManager authenticationManager;
        private final UsersRepository usersRepository;
        private final CustomUserDetailsService userDetailsService;
        private final JwtService jwtService;

        public AuthResponse authenticate(AuthRequest request) {

                // Buscar usuario
                Users user = usersRepository.findByEmail(request.getEmail())
                                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

                // Verificar si la cuenta está bloqueada permanentemente
                if (Boolean.TRUE.equals(user.getBlocked())) {
                        throw new IllegalStateException(
                                        "Esta cuenta ha sido bloqueada permanentemente. Contacte al administrador.");
                }

                // Verificar si no tiene bloqueo temporal
                if (!user.getActive()) {
                        throw new IllegalStateException("Esta cuenta ha sido desactivada por el administrador.");
                }

                // Autenticar usuario
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

                // Generar JWT
                UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
                String token = jwtService.generateToken(userDetails);

                return new AuthResponse(token, user.getEmail(), userDetails.getAuthorities().stream()
                                .map(Object::toString)
                                .toList());
        }
}

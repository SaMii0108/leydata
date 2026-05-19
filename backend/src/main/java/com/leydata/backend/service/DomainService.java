package com.leydata.backend.service;

import com.leydata.backend.dto.CreateDomainRequest;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.UserDomains;
import com.leydata.backend.entity.Users;
import com.leydata.backend.repository.DomainsRepository;
import com.leydata.backend.repository.UserDomainsRepository;
import com.leydata.backend.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DomainService {

    private final DomainsRepository domainsRepository;
    private final UsersRepository usersRepository;
    private final UserDomainsRepository userDomainsRepository;

    // CREAR DOMINIO solo ADMIN
    @Transactional
    public Domains createDomain(CreateDomainRequest request) {
        getAuthenticatedAdmin();

        if (domainsRepository.findByCode(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un dominio con ese código");
        }

        Users jefe = null;
        if (request.getJefeId() != null) {
            jefe = usersRepository.findById(request.getJefeId())
                    .orElseThrow(() -> new IllegalArgumentException("El usuario jefe no existe"));

            boolean hasJefeRole = jefe.getUserRoles().stream()
                    .anyMatch(ur -> "JEFE_DOMINIO".equals(ur.getRole().getCode()));
            if (!hasJefeRole) {
                throw new IllegalArgumentException("El usuario no tiene el rol jefe de dominio");
            }
        }

        Domains domain = new Domains();
        domain.setCode(request.getCode());
        domain.setName(request.getName());
        domain.setDescription(request.getDescription());
        domain.setActive(true); // siempre activo al crear
        domain.setCreatedAt(LocalDateTime.now());

        Domains savedDomain = domainsRepository.save(domain);

        if (jefe != null) {
            UserDomains userDomain = new UserDomains();
            UserDomains.UserDomainsId udId = new UserDomains.UserDomainsId();
            udId.setUserId(jefe.getId());
            udId.setDomainId(savedDomain.getId());
            userDomain.setId(udId);
            userDomain.setUser(jefe);
            userDomain.setDomain(savedDomain);
            userDomainsRepository.save(userDomain);
        }

        return savedDomain;
    }

    // // LISTAR DOMINIOS ACTIVOS cualquier rol autenticado
    // @Transactional(readOnly = true)
    // public List<Domains> getActiveDomains() {
    // return domainsRepository.findByActiveTrue();
    // }

    // LISTAR TODOS LOS DOMINIOS solo ADMIN (incluye inactivos)
    @Transactional(readOnly = true)
    public List<Domains> getAllDomains() {
        getAuthenticatedAdmin();
        return domainsRepository.findAll();
    }

    // DESACTIVAR DOMINIO solo ADMIN, reversible
    @Transactional
    public Domains deactivateDomain(UUID domainId) {
        getAuthenticatedAdmin();

        Domains domain = domainsRepository.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));

        if (!Boolean.TRUE.equals(domain.getActive())) {
            throw new IllegalStateException("El dominio ya está desactivado");
        }

        domain.setActive(false);
        return domainsRepository.save(domain);
    }

    // REACTIVAR DOMINIO — solo ADMIN, reversible
    @Transactional
    public Domains reactivateDomain(UUID domainId) {
        getAuthenticatedAdmin();

        Domains domain = domainsRepository.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));

        if (Boolean.TRUE.equals(domain.getActive())) {
            throw new IllegalStateException("El dominio ya está activo");
        }

        domain.setActive(true);
        return domainsRepository.save(domain);
    }

    // MÉTODO AUXILIAR PARA VALIDAR QUE EL USUARIO AUTENTICADO ES ADMIN
    private Users getAuthenticatedAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Users user = usersRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));

        boolean isAdmin = user.getUserRoles().stream()
                .anyMatch(ur -> "ADMIN".equals(ur.getRole().getCode()));
        if (!isAdmin) {
            throw new SecurityException("Acceso denegado: se requiere rol ADMIN");
        }
        return user;
    }
}
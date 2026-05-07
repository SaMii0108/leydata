package com.leydata.backend.service;

import com.leydata.backend.dto.CreateDomainRequest;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.UserDomains;
import com.leydata.backend.entity.Users;
import com.leydata.backend.repository.DomainsRepository;
import com.leydata.backend.repository.UserDomainsRepository;
import com.leydata.backend.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DomainService {

    private final DomainsRepository domainsRepository;
    private final UsersRepository usersRepository;
    private final UserDomainsRepository userDomainsRepository;

    @Transactional
    public Domains createDomain(CreateDomainRequest request) {
        Users jefe = null;
        if (request.getJefeId() != null) {
            jefe = usersRepository.findById(request.getJefeId())
                    .orElseThrow(() -> new IllegalArgumentException("El usuario jefe no existe"));

            boolean hasJefeRole = jefe.getUserRoles().stream()
                    .anyMatch(userRole -> "JEFE_DOMINIO".equals(userRole.getRole().getCode()));
            if (!hasJefeRole) {
                throw new IllegalArgumentException("El usuario especificado no tiene el rol JEFE_DOMINIO");
            }
        }

        if (domainsRepository.findByCode(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un dominio con ese código");
        }

        Domains domain = new Domains();
        domain.setCode(request.getCode());
        domain.setName(request.getName());
        domain.setDescription(request.getDescription());
        domain.setCreatedAt(LocalDateTime.now());

        Domains savedDomain = domainsRepository.save(domain);

        if (jefe != null) {
            UserDomains userDomain = new UserDomains();
            userDomain.setUser(jefe);
            userDomain.setDomain(savedDomain);
            userDomainsRepository.save(userDomain);
        }

        return savedDomain;
    }
}
package com.leydata.backend.controller;

import com.leydata.backend.dto.PurposeRequestDto;
import com.leydata.backend.entity.PurposeRequests;
import com.leydata.backend.entity.Users;
import com.leydata.backend.repository.PurposeRequestsRepository;
import com.leydata.backend.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/purpose-requests")
@RequiredArgsConstructor
public class PurposeRequestController {

    private final PurposeRequestsRepository purposeRequestsRepository;
    private final UsersRepository usersRepository;

    @PreAuthorize("hasRole('JEFE_DOMINIO')")
    @PostMapping
    public ResponseEntity<String> createPurposeRequest(
            @RequestBody PurposeRequestDto request,
            Authentication authentication) {
        Users currentUser = usersRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));

        PurposeRequests purposeRequest = new PurposeRequests();
        purposeRequest.setTitle(request.getTitle());
        purposeRequest.setJustification(request.getJustification());
        purposeRequest.setRequestedData(request.getRequestedData());
        purposeRequest.setDomainId(request.getDomainId());
        purposeRequest.setRequesterId(currentUser.getId());
        purposeRequest.setStatus("PENDING");
        purposeRequest.setCreatedAt(LocalDateTime.now());

        purposeRequestsRepository.save(purposeRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body("Purpose request created");
    }
}

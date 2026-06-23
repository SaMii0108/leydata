package com.leydata.backend.user.application.dto;

import com.leydata.backend.entity.Users;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String name;
    private Boolean active;
    private Boolean blocked;
    private List<String> roles;
    private List<String> domains;

    public static UserResponse from(Users user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getActive(),
                user.getBlocked(),
                user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getCode())
                        .toList(),
                user.getUserDomains().stream()
                        .filter(ud -> Boolean.TRUE.equals(ud.getDomain().getActive()))
                        .map(ud -> ud.getDomain().getName())
                        .toList());
    }
}

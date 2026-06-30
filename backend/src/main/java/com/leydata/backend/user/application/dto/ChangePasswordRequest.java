package com.leydata.backend.user.application.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangePasswordRequest {
    private String newPassword;
}

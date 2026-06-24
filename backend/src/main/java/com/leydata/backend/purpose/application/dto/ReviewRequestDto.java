package com.leydata.backend.purpose.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequestDto {
    private String status; // APPROVED o REJECTED
    private String reviewNotes; // obligatorio si REJECTED (Ley 21.719)
}

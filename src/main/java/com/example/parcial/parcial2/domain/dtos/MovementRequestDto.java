package com.example.parcial.parcial2.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MovementRequestDto {

    @NotBlank
    private String isbn;

    @NotBlank
    private String email;
}
package com.example.demo.client.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicFrequentClientRegistrationRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 200, message = "El nombre no puede exceder 200 caracteres")
    private String parentName;

    @NotBlank(message = "El nombre del niño es obligatorio")
    @Size(max = 150, message = "El nombre del niño no puede exceder 150 caracteres")
    private String childName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(
            regexp = "^[+0-9()\\s-]{10,24}$",
            message = "Ingresa un teléfono válido")
    private String phone;

    @Email(message = "Ingresa un correo válido")
    @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
    private String email;

    @AssertTrue(message = "Debes aceptar el uso de tus datos para registrarte")
    private boolean consentAccepted;
}

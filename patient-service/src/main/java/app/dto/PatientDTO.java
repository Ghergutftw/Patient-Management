package app.dto;

import app.dto.validators.CreatePatientValidationGroup;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Value;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 * DTO for {@link app.model.Patient}
 */
@Value
public class PatientDTO implements Serializable {

    UUID id;

    String patientCode;

    @NotBlank
    @Size(min = 3, max = 30, message = "Name must be between 3 and 30 characters")
    String name;

    @NotBlank(message = "Email is  required")
    @Email(message = "Email should be valid")
    String email;

    @NotBlank(message = "Address is required")
    String address;

    @NotNull(message = "Birth date is required")
    Date birthDate;

    @NotBlank(groups = CreatePatientValidationGroup.class, message = "Registered date is required")
    Date registeredDate;
}
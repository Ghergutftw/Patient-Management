package app.dto;

import app.dto.validators.CreatePatientValidationGroup;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.Value;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 * DTO for {@link app.model.Patient}
 */
@Value
public class PatientRequestDTO implements Serializable {

    UUID id;

    @NotBlank
    @Size(min = 3, max = 30, message = "Name must be between 3 and 30 characters")
    String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    String email;

    @NotBlank(message = "Address is required")
    @Size(min = 5, max = 100, message = "Address must be between 5 and 100 characters")
    String address;

    @NotNull(message = "Birth date is required")
    @Past(message = "Birth date must be in the past")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    Date birthDate;

    @NotNull(message = "Registered date is required", groups = CreatePatientValidationGroup.class)
    @PastOrPresent(message = "Registered date must be in the past or present", groups = CreatePatientValidationGroup.class)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    Date registeredDate;
}
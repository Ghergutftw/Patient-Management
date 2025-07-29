package app.controller;

import app.dto.PatientRequestDTO;
import app.dto.validators.CreatePatientValidationGroup;
import app.exception.EmailAlreadyExistsException;
import app.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/patients")
@Tag(name = "Patient Management", description = "Operations related to patient management")
public class PatientController {
    private static final Logger log = LoggerFactory.getLogger(PatientController.class);
    private final PatientService patientService;

    @GetMapping
    @Operation(summary = "Get all patients", description = "Retrieves a list of all registered patients")
    public ResponseEntity<List<PatientRequestDTO>> getAllPatients() {
        log.info("Retrieving all patients");
        List<PatientRequestDTO> patients = patientService.getAllPatients();
        return ResponseEntity.ok(patients);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get patient by ID", description = "Retrieves a patient by their unique ID")
    public ResponseEntity<PatientRequestDTO> getPatientById(@PathVariable UUID id) {
        log.info("Retrieving patient with id: {}", id);
        return patientService.getPatientById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Patient not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    @Operation(summary = "Create a new patient", description = "Registers a new patient in the system")
    public ResponseEntity<PatientRequestDTO> createPatient(@Validated({Default.class, CreatePatientValidationGroup.class}) @RequestBody PatientRequestDTO patientRequestDTO) throws EmailAlreadyExistsException {
        log.info("Creating new patient");
        PatientRequestDTO createdPatient = patientService.createPatient(patientRequestDTO);
        return ResponseEntity.created(URI.create("/patients/" + createdPatient.getId()))
                .body(createdPatient);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update patient information", description = "Updates the details of an existing patient")
    public ResponseEntity<PatientRequestDTO> updatePatient(@PathVariable UUID id,
                                                           @Validated({Default.class}) @RequestBody PatientRequestDTO patientRequestDTO) {
        log.info("Updating patient with id: {}", id);
        return patientService.updatePatient(id, patientRequestDTO)
                .map(updated -> {
                    log.info("Patient updated successfully");
                    return ResponseEntity.ok(updated);
                })
                .orElseGet(() -> {
                    log.warn("Patient not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete patient", description = "Deletes a patient from the system by their unique ID")
    public ResponseEntity<Void> deletePatient(@PathVariable UUID id) {
        log.info("Attempting to delete patient with id: {}", id);
        if (patientService.deletePatient(id)) {
            log.info("Patient deleted successfully");
            return ResponseEntity.noContent().build();
        } else {
            log.warn("Patient not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
package app.controller;

import app.dto.PatientDTO;
import app.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/patients")
public class PatientController {
    private static final Logger log = LoggerFactory.getLogger(PatientController.class);
    private final PatientService patientService;

    @GetMapping
    public ResponseEntity<List<PatientDTO>> getAllPatients() {
        log.info("Retrieving all patients");
        List<PatientDTO> patients = patientService.getAllPatients();
        return ResponseEntity.ok(patients);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientDTO> getPatientById(@PathVariable UUID id) {
        log.info("Retrieving patient with id: {}", id);
        return patientService.getPatientById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Patient not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    public ResponseEntity<PatientDTO> createPatient(@Valid @RequestBody PatientDTO patientDTO) {
        log.info("Creating new patient");
        PatientDTO createdPatient = patientService.createPatient(patientDTO);
        log.info("Patient created with pacient code: {}", createdPatient.getPatientCode());
        return ResponseEntity.created(URI.create("/patients/" + createdPatient.getPatientCode()))
                .body(createdPatient);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PatientDTO> updatePatient(@PathVariable UUID id,
                                                    @Valid @RequestBody PatientDTO patientDTO) {
        log.info("Updating patient with id: {}", id);
        return patientService.updatePatient(id, patientDTO)
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
package app.service;

import app.dto.PatientRequestDTO;
import app.exception.EmailAlreadyExistsException;
import app.mapper.PatientMapper;
import app.model.Patient;
import app.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional

public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    public List<PatientRequestDTO> getAllPatients() {
        log.info("Fetching all patients from the repository");
        return patientRepository.findAll().stream()
                .map(patientMapper::toPatientDTO)
                .toList();
    }
    
    public Optional<PatientRequestDTO> getPatientById(UUID id) {
        log.info("Fetching patient with id: {}", id);
        return patientRepository.findById(id)
                .map(patientMapper::toPatientDTO);
    }

    public PatientRequestDTO createPatient(PatientRequestDTO patientRequestDTO) throws EmailAlreadyExistsException {
        Patient patient = patientMapper.toPatient(patientRequestDTO);

        if(patientRepository.existsByEmail(patientRequestDTO.getEmail())) {
            log.error("Patient with email {} already exists", patientRequestDTO.getEmail());
            throw new EmailAlreadyExistsException("Patient with email " + patientRequestDTO.getEmail() + " already exists");
        }
        Patient savedPatient = patientRepository.save(patient);
        return patientMapper.toPatientDTO(savedPatient);
    }


    public Optional<PatientRequestDTO> updatePatient(UUID id, PatientRequestDTO patientRequestDTO) {
        return patientRepository.findById(id)
                .map(existingPatient -> {
                    // Update only the provided fields
                    existingPatient.setName(patientRequestDTO.getName());
                    existingPatient.setEmail(patientRequestDTO.getEmail());
                    existingPatient.setAddress(patientRequestDTO.getAddress());
                    existingPatient.setBirthDate(patientRequestDTO.getBirthDate());

                    // Only update registeredDate if it's provided in the DTO
                    if (patientRequestDTO.getRegisteredDate() != null) {
                        existingPatient.setRegisteredDate(patientRequestDTO.getRegisteredDate());
                    }
                    // Otherwise, keep the existing registeredDate unchanged

                    Patient savedPatient = patientRepository.save(existingPatient);
                    return patientMapper.toPatientDTO(savedPatient);
                });
    }
    
    public boolean deletePatient(UUID id) {
        log.info("Deleting patient with id: {}", id);
        if (patientRepository.existsById(id)) {
            patientRepository.deleteById(id);
            return true;
        }
        log.warn("Patient with id: {} not found for deletion", id);
        return false;
    }
}
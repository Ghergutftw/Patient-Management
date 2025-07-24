package app.service;

import app.dto.PatientDTO;
import app.exception.EmailAlreadyExistsException;
import app.exception.PatientNotFoundException;
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

    public List<PatientDTO> getAllPatients() {
        log.info("Fetching all patients from the repository");
        return patientRepository.findAll().stream()
                .map(patientMapper::toPatientDTO)
                .toList();
    }
    
    public Optional<PatientDTO> getPatientById(UUID id) {
        log.info("Fetching patient with id: {}", id);
        return patientRepository.findById(id)
                .map(patientMapper::toPatientDTO);
    }

    public PatientDTO createPatient(PatientDTO patientDTO) throws EmailAlreadyExistsException {
        Patient patient = patientMapper.toPatient(patientDTO);

        if(patientRepository.existsByEmail(patientDTO.getEmail())) {
            log.error("Patient with email {} already exists", patientDTO.getEmail());
            throw new EmailAlreadyExistsException("Patient with email " + patientDTO.getEmail() + " already exists");
        }
        Patient savedPatient = patientRepository.save(patient);
        return patientMapper.toPatientDTO(savedPatient);
    }
    
    public Optional<PatientDTO> updatePatient(UUID id, PatientDTO patientDTO) {
        log.info("Updating patient with id: {}", id);

        patientRepository.findById(id)
                .orElseThrow(() -> new PatientNotFoundException("Patient with id " + id + " not found"));

        return patientRepository.findById(id)
                .map(existingPatient -> {
                    Patient updatedPatient = patientMapper.toPatient(patientDTO);
                    updatedPatient.setId(id);
                    Patient savedPatient = patientRepository.save(updatedPatient);
                    return patientMapper.toPatientDTO(savedPatient);
                });
    }
    
    public boolean deletePatient(UUID id) {
        log.info("Deleting patient with id: {}", id);
        if (patientRepository.existsById(id)) {
            patientRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
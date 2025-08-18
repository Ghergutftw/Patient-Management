package app.service;

import app.dto.PatientRequestDTO;
import app.exception.EmailAlreadyExistsException;
import app.grpc.BillingServiceGrpcClient;
import app.kafka.KafkaProducer;
import app.mapper.PatientMapper;
import app.model.Patient;
import app.repository.PatientRepository;
import io.micrometer.observation.annotation.Observed;
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
@Observed
public class PatientService {

    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final PatientMapper patientMapper;
    private final KafkaProducer kafkaProducer;

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
        if (patientRepository.existsByEmail(patientRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "A patient with this email " + "already exists"
                            + patientRequestDTO.getEmail());
        }

        Patient newPatient = patientRepository.save(patientMapper.toPatient(patientRequestDTO));

        billingServiceGrpcClient.createBillingAccount(newPatient.getId().toString(),
                newPatient.getName(), newPatient.getEmail());

        kafkaProducer.sendEvent(newPatient);
        log.info("Created new patient with id: {}", newPatient.getId());

        return patientMapper.toPatientDTO(newPatient);
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
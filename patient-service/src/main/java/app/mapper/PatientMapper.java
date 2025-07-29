package app.mapper;

import app.dto.PatientRequestDTO;
import app.model.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING , unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PatientMapper {
    PatientRequestDTO toPatientDTO(Patient p);
    Patient toPatient(PatientRequestDTO pDTO);
}

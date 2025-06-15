package app.mapper;

import app.dto.PatientDTO;
import app.model.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING , unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PatientMapper {
    PatientDTO toPatientDTO(Patient p);
    Patient toPatient(PatientDTO pDTO);
}

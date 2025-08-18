package com.pm.analyticsservice.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

@Service
@Observed
public class KafkaConsumer {

  private static final Logger log = LoggerFactory.getLogger(
      KafkaConsumer.class);

  @KafkaListener(topics="patient", groupId = "analytics-service")
  public void consumeEvent(byte[] event) {
    try {
      PatientEvent patientEvent = PatientEvent.parseFrom(event);
      // ... perform any business related to analytics here

      log.info("Received Patient Event: [PatientId={},PatientName={},PatientEmail={}]",
            patientEvent.getPatientId(),
            patientEvent.getName(),
            patientEvent.getEmail());
    } catch (InvalidProtocolBufferException e) {
      log.error("Error deserializing event {}", e.getMessage());
    }
  }
}

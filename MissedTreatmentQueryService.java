package com.davita.cwow.patient.hmt.service;

import com.davita.cwow.patient.service.api.config.ServiceConstants;
import com.davita.cwow.patient.service.api.exception.PatientQueryApiException;
import com.davita.cwow.patient.service.common.FacilityQueryService;
import com.davita.cwow.patient.service.model.schedule.MissedTreatment;
import com.davita.cwow.patient.service.model.schedule.dto.HMTEventHistoryDto;
import com.davita.cwow.patient.service.model.schedule.dto.MissedTreatmentDto;
import com.davita.cwow.patient.service.repository.SrScheduleExceptionHistRepository;
import com.davita.cwow.patient.service.repository.SrScheduleExceptionRepository;
import com.davita.cwow.patient.spanner.model.converters.dto.HMTEventHistoryDtoConverter;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrScheduleExceptionToMissedTreatmentConverter;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MissedTreatmentQueryService {

	@Autowired
	private SrScheduleExceptionToMissedTreatmentConverter scheduleExceptionToMissedTreatmentConverter;
	
	@Autowired
	private SrScheduleExceptionRepository srScheduleExceptionRepository;

	@Autowired
	private SrScheduleExceptionHistRepository srScheduleExceptionHistRepository;

	@Autowired
	private HMTEventHistoryDtoConverter hmtEventHistoryDtoConverter;

	@Autowired
	private FacilityQueryService facilityQueryService;

	@Autowired
	private ModelMapper mapper;

	public Optional<MissedTreatment> getMissedTreatmentEvent(UUID patientId, UUID id) {
		return srScheduleExceptionRepository.findByPatientIdAndScheduleExceptionId(patientId, id)
				.map(scheduleExceptionToMissedTreatmentConverter::convertWithContext);
	}

	public List<MissedTreatment> getAllMissedTreatmentEvents(Object[] mpis) {
		return scheduleExceptionToMissedTreatmentConverter.convertCollectionToListWithContext(
			srScheduleExceptionRepository.findByMasterPatientIdentifier(
				Arrays.stream(mpis).map(Object::toString).collect(Collectors.toList())));
	}

	public List<MissedTreatment> getAllActiveMissedTreatmentEvents(Object[] mpis) {
		return scheduleExceptionToMissedTreatmentConverter.convertCollectionToListWithContext(
			srScheduleExceptionRepository.findAllActiveMissedTreatmentsByMpi(
				Arrays.stream(mpis).map(Object::toString).collect(Collectors.toList())));
	}

	public List<MissedTreatment> getAllActiveMissedTreatmentEvents(String mpi) {
		return scheduleExceptionToMissedTreatmentConverter.convertCollectionToListWithContext(
			srScheduleExceptionRepository.findAllActiveMissedTreatmentsByMpi(mpi));
	}

	public MissedTreatmentDto getMissedTreatmentEventDetails(UUID patientId, UUID id) throws PatientQueryApiException {

		Optional<MissedTreatment> missedTreatment = getMissedTreatmentEvent(patientId, id);
		if (!missedTreatment.isPresent()) {
			throw new PatientQueryApiException(HttpStatus.OK, ServiceConstants.MISSED_TREATMENT_NOT_FOUND);
		}

		MissedTreatmentDto missedTreatmentDto = new MissedTreatmentDto();
		mapper.map(missedTreatment.get(), missedTreatmentDto);

		List<HMTEventHistoryDto> hmtEventHistory = srScheduleExceptionHistRepository
				.findTracksByPatientIdAndScheduleExceptionId(patientId, id)
				.stream()
				.map(hmtEventHistoryDtoConverter::toDto)
				.sorted(Comparator.comparing(HMTEventHistoryDto::getDocumentDateTime).reversed())
				.collect(Collectors.toList());
		
		missedTreatmentDto.setHistory(hmtEventHistory);
		missedTreatmentDto.setScheduleExceptionTrackList(null);
		
		missedTreatmentDto.setFacilityName(
				facilityQueryService.getFacilityName(missedTreatmentDto.getFacilityNumberDocumentedAt()));
		return missedTreatmentDto;
	}
	
	public List<MissedTreatment> getAllMissedTreatmentEvents(Object [] mpis, String facilityNumber, LocalDate eventDate){
		return getAllActiveMissedTreatmentEvents(mpis).stream()
				.filter(mt -> Optional.ofNullable(mt.getFacilityNumberDocumentedAt()).isPresent())
				.filter(mt -> Optional.ofNullable(mt.getStartDate()).isPresent())
				.filter(mt -> mt.getFacilityNumberDocumentedAt().equalsIgnoreCase(facilityNumber))
				.filter(mt -> mt.getStartDate().toLocalDate().isEqual(eventDate))
				.collect(Collectors.toList());
	}
}

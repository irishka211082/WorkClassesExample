package com.davita.cwow.patient.hmt.service;

import com.davita.cwow.patient.service.api.config.ServiceConstants;
import com.davita.cwow.patient.service.api.exception.PatientQueryApiRuntimeException;
import com.davita.cwow.patient.service.common.FacilityQueryService;
import com.davita.cwow.patient.service.model.schedule.TreatingElsewhere;
import com.davita.cwow.patient.service.model.schedule.dto.HMTEventHistoryDto;
import com.davita.cwow.patient.service.model.schedule.dto.TreatingElsewhereDto;
import com.davita.cwow.patient.service.repository.SrScheduleExceptionRepository;
import com.davita.cwow.patient.spanner.model.converters.dto.HMTEventHistoryDtoConverter;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrScheduleExceptionToTreatingElsewhereConverter;
import com.davita.cwow.patient.spanner.model.schedule.SrScheduleException;
import org.apache.commons.collections.CollectionUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TreatingElsewhereQueryService {

	@Autowired
	private SrScheduleExceptionRepository srScheduleExceptionRepository;

	@Autowired
	private FacilityQueryService facilityQueryService;

	@Autowired
	private ModelMapper mapper;

	@Autowired
	private SrScheduleExceptionToTreatingElsewhereConverter scheduleExceptionToTreatingElsewhereConverter;

	@Autowired
	private HMTEventHistoryDtoConverter hmtEventHistoryDtoConverter;

	public List<TreatingElsewhere> getAllActiveTreatingElsewhereEvents(Object[] mpis) {
		return scheduleExceptionToTreatingElsewhereConverter.convertCollectionToListWithContext(
			srScheduleExceptionRepository.findAllActiveTreatingElsewhereByMpi(
				Arrays.stream(mpis).map(Object::toString).collect(Collectors.toList())));
	}

	public List<TreatingElsewhere> getAllActiveTreatingElsewhereEvents(String mpi) {
		return scheduleExceptionToTreatingElsewhereConverter.convertCollectionToListWithContext(
			srScheduleExceptionRepository.findAllActiveTreatingElsewhereByMpi(mpi));
	}

	public TreatingElsewhereDto getTreatingElsewhereEventDetails(String mpi, UUID id) {
		SrScheduleException srScheduleException = srScheduleExceptionRepository.findByMpiAndScheduleExceptionId(mpi, id)
				.orElseThrow(() -> new PatientQueryApiRuntimeException(ServiceConstants.SCHEDULE_EXCEPTION_NOT_FOUND));

		TreatingElsewhere treatingElsewhere = scheduleExceptionToTreatingElsewhereConverter.convertWithContext(srScheduleException);

		TreatingElsewhereDto treatingElsewhereDto = new TreatingElsewhereDto();
		mapper.map(treatingElsewhere, treatingElsewhereDto);

		List<HMTEventHistoryDto> hmtEventHistory = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(srScheduleException.getScheduleExceptionHists())) {
			srScheduleException.getScheduleExceptionHists().forEach(sTrack -> {
				HMTEventHistoryDto event = hmtEventHistoryDtoConverter.toDto(sTrack);
				hmtEventHistory.add(event);
			});
		}
		
		treatingElsewhereDto.setHistory(hmtEventHistory.stream()
				.sorted(Comparator.comparing(HMTEventHistoryDto::getDocumentDateTime).reversed())
				.collect(Collectors.toList()));
		
		treatingElsewhereDto.setScheduleExceptionTrackList(null);
		
		treatingElsewhereDto.setFacilityName(
				facilityQueryService.getFacilityName(treatingElsewhereDto.getFacilityNumberDocumentedAt()));

		return treatingElsewhereDto;
	}
	
	public List<TreatingElsewhere> getAllTreatingElsewhereEvents(Object [] mpis, String facilityNumber, LocalDate eventDate){
		return getAllActiveTreatingElsewhereEvents(mpis).stream()
				.filter(te -> te.getFacilityNumberDocumentedAt().equalsIgnoreCase(facilityNumber))
				.filter(te -> Optional.ofNullable(te.getFacilityNumberDocumentedAt()).isPresent())
				.filter(te -> Optional.ofNullable(te.getStartDate()).isPresent())
				.filter(te -> Optional.ofNullable(te.getEndDate()).isPresent())
				.filter(te -> ( te.getStartDate().toLocalDate().isBefore(eventDate) || te.getStartDate().toLocalDate().isEqual(eventDate)))
				.filter(te -> ( te.getEndDate().toLocalDate().isAfter(eventDate) || te.getEndDate().toLocalDate().isEqual(eventDate)))
				.collect(Collectors.toList());
	}
}

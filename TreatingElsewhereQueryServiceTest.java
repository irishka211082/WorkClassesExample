package com.davita.cwow.patient.hmt.service;

import com.davita.cwow.model.domain.ref.codesets.CodeConceptRefData;
import com.davita.cwow.patient.service.api.config.ServiceConstants;
import com.davita.cwow.patient.service.api.exception.PatientQueryApiRuntimeException;
import com.davita.cwow.patient.service.common.FacilityQueryService;
import com.davita.cwow.patient.service.model.schedule.TreatingElsewhere;
import com.davita.cwow.patient.service.model.schedule.dto.HMTEventHistoryDto;
import com.davita.cwow.patient.service.model.schedule.dto.TreatingElsewhereDto;
import com.davita.cwow.patient.service.repository.SrScheduleExceptionRepository;
import com.davita.cwow.patient.spanner.enricher.UserEnteredBaseAggregateEnricher;
import com.davita.cwow.patient.spanner.model.converters.dto.HMTEventHistoryDtoConverter;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrScheduleExceptionHistToScheduleExceptionTrackConverter;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrScheduleExceptionToTreatingElsewhereConverter;
import com.davita.cwow.patient.spanner.model.schedule.SrScheduleException;
import com.davita.cwow.patient.spanner.model.schedule.SrScheduleExceptionHist;
import com.davita.cwow.patient.spanner.service.PatientMpiService;
import com.davita.cwow.patient.spanner.util.DateTimeUtils;

import java.util.ArrayList;
import java.util.Collections;

import com.davita.cwow.patient.spanner.util.EntityUtil;
import com.davita.cwow.patient.spanner.util.UuidUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.modelmapper.ModelMapper;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TreatingElsewhereQueryServiceTest {

    @InjectMocks
    private TreatingElsewhereQueryService treatingElsewhereQueryService;

    @Mock
    private SrScheduleExceptionRepository srScheduleExceptionRepository;

    @Mock
    private FacilityQueryService facilityQueryService;

    @Mock
    private PatientMpiService patientMpiService;

    @Spy
    private SrScheduleExceptionToTreatingElsewhereConverter converter =
        new SrScheduleExceptionToTreatingElsewhereConverter(
            mock(UserEnteredBaseAggregateEnricher.class), mock(PatientMpiService.class),
            mock(SrScheduleExceptionHistToScheduleExceptionTrackConverter.class));

    @Mock
    private HMTEventHistoryDtoConverter hmtEventHistoryDtoConverter;

    @Spy
    private ModelMapper mapper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getTreatingElsewhereEventDetailsTest() {
        String mpi = "876543";
        String treatingId = "8fd24036-e959-4d45-9cf6-6b0a1f410961";

        SrScheduleException srScheduleException = createScheduleException(mpi, treatingId);
        TreatingElsewhereDto expectedDto = createExpectedDto(mpi);

        doReturn(Optional.of(srScheduleException)).when(srScheduleExceptionRepository).
                findByMpiAndScheduleExceptionId(mpi, UUID.fromString(treatingId));

        when(hmtEventHistoryDtoConverter.toDto(any(SrScheduleExceptionHist.class))).thenReturn(toDto(prepareHist(srScheduleException)));

        TreatingElsewhereDto actualTreatingDto =
                treatingElsewhereQueryService.getTreatingElsewhereEventDetails(mpi, UUID.fromString(treatingId));

        assertEquals(expectedDto.getReasonTxt(), actualTreatingDto.getReasonTxt());
        assertEquals(expectedDto.getReason(), actualTreatingDto.getReason());
    }

    @Test
    public void getTreatingElsewhereDetailsTestNotFound() {
        doReturn(Optional.empty()).when(srScheduleExceptionRepository).
                findByMpiAndScheduleExceptionId(any(), any(UUID.class));

        PatientQueryApiRuntimeException exception = assertThrows(PatientQueryApiRuntimeException.class, () -> {
            treatingElsewhereQueryService.getTreatingElsewhereEventDetails("100500", UUID.randomUUID());
        });

        assertEquals(ServiceConstants.SCHEDULE_EXCEPTION_NOT_FOUND, exception.getMessage());
    }

    private List<SrScheduleException> prepareScheduleExceptions() {
        SrScheduleException scheduleExceptionIsActiveFalse = createScheduleException("100500", UUID.randomUUID().toString());
        scheduleExceptionIsActiveFalse.setDeleteDateTimeGmt(new Timestamp(12345));

        SrScheduleException scheduleExceptionEnteredInErrorFlagFalse = createScheduleException("100501", UUID.randomUUID().toString());
        scheduleExceptionEnteredInErrorFlagFalse.setFacilityNumberDocumentedAt("123");
        scheduleExceptionEnteredInErrorFlagFalse.setExceptionStartDate(
                Timestamp.valueOf(LocalDateTime.of(2019, Month.AUGUST, 29, 19, 30, 40)));
        scheduleExceptionEnteredInErrorFlagFalse.setExceptionEndDate(
                Timestamp.valueOf(LocalDateTime.of(2019, Month.OCTOBER, 29, 19, 30, 40)));
        scheduleExceptionEnteredInErrorFlagFalse.setEnteredInErrorFlag(false);

        SrScheduleException scheduleExceptionIsActiveTrue = createScheduleException("100502", UUID.randomUUID().toString());
        SrScheduleException scheduleExceptionEnteredInErrorFlagTrue = createScheduleException("100503", UUID.randomUUID().toString());

        return Arrays.asList(
                scheduleExceptionIsActiveFalse,
                scheduleExceptionEnteredInErrorFlagFalse,
                scheduleExceptionIsActiveTrue,
                scheduleExceptionEnteredInErrorFlagTrue
        );
    }

    @Test
    public void getAllActiveTreatingElsewhereEvents() {
        List<SrScheduleException> srModels = prepareScheduleExceptions();

        doReturn(Collections.singletonList(srModels.get(1)))
            .when(srScheduleExceptionRepository).findAllActiveTreatingElsewhereByMpi(anyList());
        doReturn("100500").when(patientMpiService).getMpiByPatientId(srModels.get(0).getPatientId());
        doReturn("100501").when(patientMpiService).getMpiByPatientId(srModels.get(1).getPatientId());
        doReturn("100502").when(patientMpiService).getMpiByPatientId(srModels.get(2).getPatientId());
        doReturn("100503").when(patientMpiService).getMpiByPatientId(srModels.get(3).getPatientId());

        List<TreatingElsewhere> actualTreatings = treatingElsewhereQueryService.getAllActiveTreatingElsewhereEvents(
                srModels.stream()
                        .map(e -> patientMpiService.getMpiByPatientId(e.getPatientId()))
                        .toArray());

        assertNotNull(actualTreatings);
        assertNotNull(srModels.get(0).getScheduleExceptionHists());
        assertEquals(1, actualTreatings.size());

        assertEquals(srModels.get(1).getScheduleExceptionId(), actualTreatings.get(0).getId());
    }

    @Test
    public void getAllActiveTreatingElsewhereEventsByMpi() {
        List<SrScheduleException> srModels = prepareScheduleExceptions();

        doReturn(Collections.singletonList(srModels.get(1)))
            .when(srScheduleExceptionRepository).findAllActiveTreatingElsewhereByMpi(anyString());

        List<TreatingElsewhere> actualResult =
            treatingElsewhereQueryService.getAllActiveTreatingElsewhereEvents("100501");

        assertEquals(1, actualResult.size());
        assertEquals(srModels.get(1).getScheduleExceptionId(), actualResult.get(0).getId());
    }

    @Test
    public void getAllTreatingElsewhereEvents() {
        List<SrScheduleException> srModels = prepareScheduleExceptions();

        doReturn(srModels).when(srScheduleExceptionRepository).findAllActiveTreatingElsewhereByMpi(anyList());

        List<TreatingElsewhere> actualTreatings = treatingElsewhereQueryService.getAllTreatingElsewhereEvents(
                new Object[]{"100500", "100501", "100502", "100503", "100504"},
                "123",
                LocalDate.of(2019, Month.SEPTEMBER, 29));

        assertNotNull(actualTreatings);
        assertEquals(1, actualTreatings.size());
        assertEquals(3, srModels.get(1).getScheduleExceptionHists().size());
        assertEquals(srModels.get(1).getScheduleExceptionId(), actualTreatings.get(0).getId());
    }

    private SrScheduleException createScheduleException(String mpi, String treatingId) {
        SrScheduleException srScheduleException = SrScheduleException.builder()
                .patientId(UuidUtils.fromStringKey(mpi))
                .scheduleExceptionId(UUID.fromString(treatingId))
                .facilityNumberDocumentedAt("facility number documented at")
                .enteredInErrorFlag(true)
                .exceptionTypeDisplayName("display name")
                .exceptionTypeRecordNumber(12)
                .locationName("location name")
                .reasonCodeId("code id")
                .reasonDisplay("display name")
                .reasonTxt("reason txt")
                .admissionReasonCodeId("Admission Reason Code Id")
                .admissionReasonDisplay("Admission Reason Display")
                .admissionReasonTxt("Admission Reason Txt")
                .hospitalId("hospital id")
                .hospitalName("hospital name")
                .sourceCodeId("source code id")
                .sourceDisplay("source display")
                .sourceTxt("source txt")
                .rescheduledToDate(DateTimeUtils.currentTimestampUTC())
                .exceptionStartDate(DateTimeUtils.currentTimestampUTC())
                .exceptionEndDate(DateTimeUtils.currentTimestampUTC())
                .recordVersion(12)
                .createSessionTokenId("create session code id")
                .updateSessionTokenId("update session code id")
                .updateDateTimeGmt(DateTimeUtils.currentTimestampUTC())
                .createDateTimeGmt(DateTimeUtils.currentTimestampUTC())
                .build();

        srScheduleException.setScheduleExceptionHists(prepareScheduleExceptionHists(srScheduleException));

        return srScheduleException;
    }

    private SrScheduleExceptionHist prepareHist(SrScheduleException srScheduleException){
        return new SrScheduleExceptionHist(srScheduleException);
    }

    private List<SrScheduleExceptionHist> prepareScheduleExceptionHists(SrScheduleException srScheduleException) {
        List<SrScheduleExceptionHist> hists = new ArrayList<>();
        hists.add(prepareHist(srScheduleException));
        hists.add(prepareHist(srScheduleException));
        hists.add(prepareHist(srScheduleException));
        return hists;
    }

    private TreatingElsewhereDto createExpectedDto(String mpi) {
        TreatingElsewhereDto expectedDto = new TreatingElsewhereDto();
        expectedDto.setLocationName("location name");
        expectedDto.setMasterPatientIdentifier(mpi);
        expectedDto.setReasonTxt("reason txt");
        expectedDto.setFacilityNumberDocumentedAt("facility number documented at");
        CodeConceptRefData reason = new CodeConceptRefData();
        reason.setDisplayName("display name");
        reason.setCodeId("code id");
        expectedDto.setReason(reason);

        return expectedDto;
    }

    private HMTEventHistoryDto toDto(SrScheduleExceptionHist srScheduleExceptionHist) {
        if (Objects.isNull(srScheduleExceptionHist)) {
            return null;
        }

        HMTEventHistoryDto hmtEventHistoryDto = new HMTEventHistoryDto();

        hmtEventHistoryDto.setExceptionType(EntityUtil.getAppRefDataPersist(
                srScheduleExceptionHist.getExceptionTypeRecordNumber(),
                srScheduleExceptionHist.getExceptionTypeDisplayName()));

        hmtEventHistoryDto.setDocumentDateTime(DateTimeUtils.zonedDateTimeUTCFromTimestamp(
                srScheduleExceptionHist.getCreateDateTimeGmt()));

        return hmtEventHistoryDto;
    }
}

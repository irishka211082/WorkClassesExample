package com.davita.cwow.patient.hmt.service;

import com.davita.cwow.patient.service.api.exception.PatientQueryApiException;
import com.davita.cwow.patient.service.common.FacilityQueryService;
import com.davita.cwow.patient.service.model.schedule.MissedTreatment;
import com.davita.cwow.patient.service.model.schedule.ScheduleExceptionTrack;
import com.davita.cwow.patient.service.repository.SrScheduleExceptionHistRepository;
import com.davita.cwow.patient.service.repository.SrScheduleExceptionRepository;
import com.davita.cwow.patient.spanner.model.converters.dto.HMTEventHistoryDtoConverter;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrScheduleExceptionToMissedTreatmentConverter;
import com.davita.cwow.patient.spanner.model.schedule.SrScheduleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.modelmapper.ModelMapper;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class MissedTreatmentQueryServiceTest {

    @InjectMocks
    private MissedTreatmentQueryService missedTreatmentQueryService;

    @Mock
    private SrScheduleExceptionToMissedTreatmentConverter scheduleExceptionToMissedTreatmentConverter;
    
    @Mock
    private SrScheduleExceptionRepository scheduleExceptionRepository;

    @Mock
    private SrScheduleExceptionHistRepository srScheduleExceptionHistRepository;

    @Mock
    private HMTEventHistoryDtoConverter hmtEventHistoryDtoConverter;

    @Mock
    private HMTEventQueryService htmEventsQueryService;

    @Mock
    private FacilityQueryService facilityQueryService;

    @Spy
    private ModelMapper mapper;

    private MissedTreatmentQueryService missedTreatmentQueryServiceSpy;
    private MissedTreatment missedTreatment;
    private UUID id;
    private UUID patientId;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        missedTreatment = new MissedTreatment();
        id = UUID.randomUUID();
        patientId = UUID.randomUUID();
        missedTreatment.setId(id);
        missedTreatment.setIsEnteredInError(false);
        missedTreatment.setIsActive(true);
        missedTreatment.setStartDate(ZonedDateTime.now());
        missedTreatment.setFacilityNumberDocumentedAt("03436");
        Set<ScheduleExceptionTrack> trackList = new HashSet<>();
        ScheduleExceptionTrack exceptionTrack = new ScheduleExceptionTrack();
        exceptionTrack.setAdmissionReasonTxt("Reason text");
        trackList.add(exceptionTrack);
        missedTreatment.setScheduleExceptionTrackList(trackList);
        missedTreatmentQueryServiceSpy = Mockito.spy(missedTreatmentQueryService);
    }

    @Test
    public void getMissedTreatmentEventTest() {
        SrScheduleException srScheduleException = mock(SrScheduleException.class);

        doReturn(Optional.of(srScheduleException))
                .when(scheduleExceptionRepository).findByPatientIdAndScheduleExceptionId(patientId, id);
        doReturn(missedTreatment)
                .when(scheduleExceptionToMissedTreatmentConverter).convertWithContext(srScheduleException);

        Optional<MissedTreatment> missedTreatment = missedTreatmentQueryService.getMissedTreatmentEvent(patientId, id);

        assertTrue(missedTreatment.isPresent());
        assertEquals(missedTreatment.get().getId(), id);
    }

    @Test
    public void getAllMissedTreatmentEventsTest() {
        doReturn(Collections.singletonList(missedTreatment))
            .when(scheduleExceptionToMissedTreatmentConverter).convertCollectionToListWithContext(any());
        assertEquals(missedTreatmentQueryService.getAllMissedTreatmentEvents(new Object[]{"123456"}).get(0).getId(), id);
    }

    @Test
    public void getMissedTreatmentEventDetailsTest() throws PatientQueryApiException {
        doReturn(Optional.of(missedTreatment)).when(missedTreatmentQueryServiceSpy).getMissedTreatmentEvent(patientId, id);
        doReturn(Collections.emptyList()).when(srScheduleExceptionHistRepository).findTracksByPatientIdAndScheduleExceptionId(patientId, id);
        doReturn("Sumner Dialysis").when(facilityQueryService).getFacilityName(any());
        assertEquals(id, missedTreatmentQueryServiceSpy.getMissedTreatmentEventDetails(patientId, id).getId());
    }

    @Test
    public void getMissedTreatmentDetailsTestNotFound() {
        doReturn(Optional.ofNullable(null)).when(missedTreatmentQueryServiceSpy).getMissedTreatmentEvent(patientId, id);
        assertThatExceptionOfType(PatientQueryApiException.class)
                .isThrownBy(() -> missedTreatmentQueryServiceSpy.getMissedTreatmentEventDetails(patientId, id))
                .withMessage("Missed Treatment not found.");
    }

    @Test
    public void getAllActiveMissedTreatmentEventsByMpiListTest() {
        List<MissedTreatment> missedTreatments = new ArrayList<>();
        missedTreatments.add(missedTreatment);
        doReturn(missedTreatments).when(scheduleExceptionToMissedTreatmentConverter).convertCollectionToListWithContext(any());
        assertEquals(1, missedTreatmentQueryServiceSpy.getAllActiveMissedTreatmentEvents(new Object[]{"123"}).size());
    }

    @Test
    public void getAllActiveMissedTreatmentEventsByMpiTest() {
        List<MissedTreatment> missedTreatments = new ArrayList<>();
        missedTreatments.add(missedTreatment);
        doReturn(missedTreatments).when(scheduleExceptionToMissedTreatmentConverter).convertCollectionToListWithContext(any());
        assertEquals(1, missedTreatmentQueryServiceSpy.getAllActiveMissedTreatmentEvents("123").size());
    }

    @Test
    public void getAllMissedTreatmentEventsWithGivenFacilityTest() {
        List<MissedTreatment> missedTreatments = new ArrayList<>();
        missedTreatments.add(missedTreatment);
        doReturn(missedTreatments).when(scheduleExceptionToMissedTreatmentConverter).convertCollectionToListWithContext(any());
        assertEquals(1, missedTreatmentQueryServiceSpy.getAllMissedTreatmentEvents(
            new Object[]{"123"}, "03436", LocalDate.now()).size());
    }
}

package com.davita.cwow.patient.service;

import com.davita.cwow.es.restclient.service.SearchService;
import com.davita.cwow.grid.config.GridOperation;
import com.davita.cwow.model.base.spanner.DateUtils;
import com.davita.cwow.model.domain.app_ref.AppRefDataPersist;
import com.davita.cwow.model.domain.ref.codesets.CodeConceptRefData;
import com.davita.cwow.patient.service.api.config.ApplicationConfiguration;
import com.davita.cwow.patient.service.api.exception.PatientQueryApiException;
import com.davita.cwow.patient.service.model.Patient;
import com.davita.cwow.patient.service.model.PatientLanguage;
import com.davita.cwow.patient.service.model.dto.PatientProfileDto;
import com.davita.cwow.patient.service.model.dto.PatientResponse;
import com.davita.cwow.patient.service.model.status.PatientStatus;
import com.davita.cwow.patient.service.repository.SrPatientRepository;
import com.davita.cwow.patient.service.repository.SrPatientStatusRepository;
import com.davita.cwow.patient.service.util.ElasticSearchUtil;
import com.davita.cwow.patient.service.util.PatientQueryTestUtil;
import com.davita.cwow.patient.spanner.enricher.UserEnteredBaseAggregateEnricher;
import com.davita.cwow.patient.spanner.enricher.UserEnteredBaseDomainEnricher;
import com.davita.cwow.patient.spanner.model.SrPatient;
import com.davita.cwow.patient.spanner.model.SrPatientContact;
import com.davita.cwow.patient.spanner.model.SrPatientLanguage;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrPatientContactConverter;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrPatientConverter;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrPatientStatusConverter;
import com.davita.cwow.patient.spanner.model.status.SrPatientStatus;
import com.davita.cwow.patient.spanner.service.PatientMpiService;
import com.davita.cwow.pillars.model.DvaPatientMaster;
import com.davita.cwow.pillars.model.Name;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PatientProfileQueryServiceTest {

    @InjectMocks
    PatientProfileQueryService patientProfileQueryService;

    @Mock
    private SrPatientRepository patientRepository;

    @Mock
    private SrPatientStatusRepository patientStatusRepository;

    @Mock
    private GridOperation gridOp;

    @Mock
    private PatientStatusQueryService patientStatusQueryService;

    @Mock
    private ElasticSearchUtil esutil;

    @Mock
    private SearchService esSearchService;

    @Mock
    private ObjectMapper jacksonObjectMapper;

    @Mock
    private ApplicationConfiguration appConfig;

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private SrPatientConverter patientConverter = new SrPatientConverter(mock(UserEnteredBaseDomainEnricher.class));

    @Spy
    private SrPatientStatusConverter patientStatusConverter = new SrPatientStatusConverter(
            mock(UserEnteredBaseAggregateEnricher.class),
            mock(PatientMpiService.class));

    @Spy
    private SrPatientContactConverter patientContactConverter;

    DvaPatientMaster dvaPatientMaster = new DvaPatientMaster();

    PatientResponse response = new PatientResponse();

    ObjectMapper objectMapper;

    private String mpi;
    private String facility;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mpi = Integer.toString(RandomUtils.nextInt());
        facility = Integer.toString(RandomUtils.nextInt());

        objectMapper = PatientQueryTestUtil.getObjectMapper();

        String responseJson = PatientQueryTestUtil.readFile("DvaPatient.json");
        dvaPatientMaster = objectMapper.readValue(responseJson, DvaPatientMaster.class);

        String dvaPatientMasterResp = PatientQueryTestUtil.readFile("DvaPatientMasterPillars.json");
        response = objectMapper.readValue(dvaPatientMasterResp, PatientResponse.class);
    }

    @Test
    public void getPatientProfileDetails() throws PatientQueryApiException {
        PatientProfileDto expectedDto = prepareProfileDto();
        SrPatient srPatient = prepareSrPatient();

        when(patientRepository.findByMasterPatientIdentifier(mpi)).thenReturn(Optional.of(srPatient));
        when(patientStatusRepository.findByMasterPatientIdentifier(mpi)).thenReturn(prepareStatuses());
        when(esutil.getDvaPatientFromPillars(mpi)).thenReturn(response);

        Map<String, Object> denodoOutputMap = new HashMap<>();
        ArrayList<Map<String, Object>> elementsList = new ArrayList<>();
        denodoOutputMap.put("elements", elementsList);
        when(restTemplate.getForObject(ArgumentMatchers.any(String.class), ArgumentMatchers.eq(Map.class), ArgumentMatchers.eq(mpi))).thenReturn(denodoOutputMap);
        when(appConfig.getPatientCoverageUrl()).thenReturn("https://denodo6dev.davita.com/i_coverageall?patient_id={mpi}");

        PatientProfileDto actualDto = patientProfileQueryService.getPatientProfileDetails(mpi);

        assertNotNull(actualDto);
        assertNotNull(actualDto.getPatient());
        assertEquals(expectedDto.getPatient().getMasterPatientIdentifier(), actualDto.getPatient().getMasterPatientIdentifier());
        assertEquals(expectedDto.getEthnicity(), actualDto.getEthnicity());
        assertEquals(expectedDto.getAdministrativeGender(), actualDto.getAdministrativeGender());
        assertEquals(expectedDto.getEntityName(), actualDto.getEntityName());
        assertEquals(expectedDto.getAge(), actualDto.getAge());

        assertEquals(expectedDto.getTranslatorText(), actualDto.getTranslatorText());
        assertEquals(expectedDto.getPrimaryLanguage().getLanguageType().getRecordNumber(),
                BigInteger.valueOf(srPatient.getPatientLanguages().get(1).getLanguageTypeRecordNo()));
        assertEquals(expectedDto.getSecondaryLanguage().getLanguageType().getRecordNumber(),
                BigInteger.valueOf(srPatient.getPatientLanguages().get(1).getLanguageTypeRecordNo()));
        assertNotEquals(actualDto.getPrimaryLanguage().getLanguageType().getRecordNumber(),
                actualDto.getSecondaryLanguage().getLanguageType().getRecordNumber());

        assertNotNull(actualDto.getPatientStatus());
        assertEquals(
                expectedDto.getPatientStatus().getPatientStatusValue().getCodeId(),
                actualDto.getPatientStatus().getPatientStatusValue().getCodeId()
        );

        assertNotNull(actualDto.getPrimaryAddress());
        assertEquals(1, actualDto.getPrimaryAddress().size());
        assertEquals("Grand Rapids", actualDto.getPrimaryAddress().get(0).getCityName());

        assertNotNull(actualDto.getSortedContactList());
        assertEquals(2, actualDto.getSortedContactList().size());
        assertEquals("Mobile", actualDto.getSortedContactList().get(0).getContactType().getDisplayName());
        assertEquals("4954064417", actualDto.getSortedContactList().get(1).getContactValue());
    }

    private PatientProfileDto prepareProfileDto() {
        PatientProfileDto profileDto = new PatientProfileDto();
        Patient patient = new Patient();
        patient.setMasterPatientIdentifier(mpi);

        profileDto.setTranslatorText("translator text");
        profileDto.setAdministrativeGender("administrative gender");
        profileDto.setEthnicity("ethnicity");
        profileDto.setAge(1);
        profileDto.setDob("2019-05-16 20:20:20.0");
        profileDto.setTranslatorText("Yes");

        PatientLanguage patientLanguage = new PatientLanguage();
        AppRefDataPersist languageType = new AppRefDataPersist();
        languageType.setRecordNumber(BigInteger.valueOf(528));
        profileDto.setPrimaryLanguage(patientLanguage);

        languageType.setRecordNumber(BigInteger.valueOf(529));
        patientLanguage.setLanguageType(languageType);
        profileDto.setSecondaryLanguage(patientLanguage);

        Name entityName = new Name();
        entityName.setFirstName("firstName");
        entityName.setMiddleName("middleName");
        entityName.setFamilyName("familyName");
        entityName.setFullName("firstName middleName familyName");
        profileDto.setEntityName(entityName);

        PatientStatus patientStatus = PatientStatus.builder()
                .patientStatusValue(CodeConceptRefData.builder().codeId("code id-1").build())
                .build();
        profileDto.setPatientStatus(patientStatus);
        profileDto.setPatient(patient);

        return profileDto;
    }

    private List<SrPatientStatus> prepareStatuses() {
        return Lists.newArrayList(
                SrPatientStatus.builder()
                        .createDateTimeGmt(Timestamp.valueOf(
                                LocalDateTime.of(2018, Month.APRIL, 10, 10, 10, 10)))
                        .patientStatusCodeId("code id-1")
                        .facilityNumber(facility)
                        .build(),
                SrPatientStatus.builder()
                        .createDateTimeGmt(Timestamp.valueOf(
                                LocalDateTime.of(2018, Month.MARCH, 10, 10, 10, 10)))
                        .patientStatusCodeId("code id-2")
                        .deleteDateTimeGmt(Timestamp.valueOf(LocalDateTime.now()))
                        .facilityNumber(facility)
                        .build()
        );
    }

    private SrPatient prepareSrPatient() {
        return SrPatient.builder()
                .masterPatientIdentifier(mpi)
                .firstName("firstName")
                .middleName("middleName")
                .lastName("familyName")
                .gender("administrative gender")
                .ethnicity("ethnicity")
                .gender("administrative gender")
                .dateOfBirth(Timestamp.valueOf(LocalDateTime.of(2019, Month.MAY, 16, 20, 20, 20)))
                .patientLanguages(preparePatientLanguages())
                .patientStatuses(prepareStatuses())
                .patientContacts(Lists.emptyList())
                .build();
    }

    private List<SrPatientLanguage> preparePatientLanguages() {
        return Lists.newArrayList(
                SrPatientLanguage.builder()
                        .patientLanguageId(UUID.randomUUID())
                        .translatorNeededFlag(true)
                        .languageTypeRecordNo(528)
                        .build(),
                SrPatientLanguage.builder()
                        .patientLanguageId(UUID.randomUUID())
                        .translatorNeededFlag(true)
                        .languageTypeRecordNo(529)
                        .build());
    }

    @Test
    public void testGetPatientProfileDetails_whenBundleFlagIndicatorNo() throws Exception {
        when((patientRepository).findByMasterPatientIdentifier(mpi)).thenReturn(Optional.of(createSrPatient()));
        when(patientStatusQueryService.getAllPatientStatus(mpi, facility))
                .thenReturn(Collections.singletonList(createPatientStatus("Pending")));
        when((esutil).getDvaPatientFromPillars(mpi)).thenReturn(response);
        when(appConfig.getPatientCoverageUrl()).thenReturn("https://denodo6dev.davita.com/i_coverageall?patient_id={mpi}");

        Map<String, Object> denodoOutputMap = new HashMap<>();
        ArrayList<Map<String, Object>> elementsList = new ArrayList<>();
        denodoOutputMap.put("elements", elementsList);
        Map<String, Object> innerMap = createInnerMap("Y", null);
        Map<String, Object> innerMap1 = createInnerMap("N", "2017-07-11T04:00:00+0000");

        elementsList.add(innerMap);
        elementsList.add(innerMap1);

        when(restTemplate.getForObject(ArgumentMatchers.any(String.class), ArgumentMatchers.eq(Map.class), ArgumentMatchers.eq(mpi))).thenReturn(denodoOutputMap);

        patientProfileQueryService.getPatientProfileDetails(mpi);
        when((patientRepository).findByMasterPatientIdentifier(anyString())).thenReturn(Optional.empty());
        when((esutil).getDvaPatientFromPillars(anyString())).thenReturn(response);
        PatientProfileDto dto = patientProfileQueryService.getPatientProfileDetails(mpi);
        assertEquals("Yes", dto.getIsMedicareBundlePresent());
        assertEquals(ZonedDateTime.parse("2017-07-01T04:00Z"), dto.getEffectiveStartDate());
        assertNull(dto.getEffectiveEndDate());
    }

    @Test
    public void testGetPatientProfileDetails_whenBundleFlagIndicatorYes() throws Exception {
        List<PatientStatus> patientStatusList = new ArrayList<>();
        patientStatusList.add(new PatientStatus());
        when((patientRepository).findByMasterPatientIdentifier(mpi)).thenReturn(Optional.of(createSrPatient()));
        when(patientStatusQueryService.getAllPatientStatus(mpi, facility)).thenReturn(patientStatusList);
        when((esutil).getDvaPatientFromPillars(mpi)).thenReturn(response);
        when(appConfig.getPatientCoverageUrl()).thenReturn("https://denodo6dev.davita.com/i_coverageall?patient_id={mpi}");

        Map<String, Object> denodoOutputMap = new HashMap<>();
        ArrayList<Map<String, Object>> elementsList = new ArrayList<>();
        denodoOutputMap.put("elements", elementsList);
        Map<String, Object> innerMap1 = createInnerMap("N", null);
        Map<String, Object> innerMap2 = createInnerMap("Y", "2017-07-11T04:00:00+0000");

        elementsList.add(innerMap1);
        elementsList.add(innerMap2);

        when(restTemplate.getForObject(ArgumentMatchers.any(String.class), ArgumentMatchers.eq(Map.class), ArgumentMatchers.eq(mpi))).thenReturn(denodoOutputMap);

        patientProfileQueryService.getPatientProfileDetails(mpi);
        when((patientRepository).findByMasterPatientIdentifier(mpi)).thenReturn(Optional.empty());
        when((esutil).getDvaPatientFromPillars(mpi)).thenReturn(response);
        PatientProfileDto dto = patientProfileQueryService.getPatientProfileDetails(mpi);
        assertEquals("No", dto.getIsMedicareBundlePresent());
        assertNull(dto.getEffectiveStartDate());
        assertNull(dto.getEffectiveEndDate());
    }

    @Test
    public void testGetPatientProfileDetailsTranslator() throws Exception {
        SrPatient cachePatient = createSrPatient();
        cachePatient.getPatientLanguages().get(0).setTranslatorNeededFlag(true);
        when((patientRepository).findByMasterPatientIdentifier(mpi)).thenReturn(Optional.of(cachePatient));
        when(appConfig.getPatientCoverageUrl()).thenReturn("https://denodo6dev.davita.com/i_coverageall?patient_id={mpi}");
        when((esutil).getDvaPatientFromPillars(mpi)).thenReturn(response);

        Map<String, Object> denodoOutputMap = new HashMap<>();
        ArrayList<Map<String, Object>> elementsList = new ArrayList<>();
        denodoOutputMap.put("elements", elementsList);
        Map<String, Object> innerMap = createInnerMap("Y", "2017-07-11T04:00:00+0000");

        elementsList.add(innerMap);

        when(restTemplate.getForObject(ArgumentMatchers.any(String.class), ArgumentMatchers.eq(Map.class), ArgumentMatchers.eq(mpi))).thenReturn(denodoOutputMap);

        patientProfileQueryService.getPatientProfileDetails(mpi);
        when((patientRepository).findByMasterPatientIdentifier(mpi)).thenReturn(Optional.empty());
        when((esutil).getDvaPatientFromPillars(mpi)).thenReturn(response);
        PatientProfileDto dto = patientProfileQueryService.getPatientProfileDetails(mpi);
        assertEquals("Yes", dto.getIsMedicareBundlePresent());
    }

    @Test
    public void testGetPatientProfileDetailsException() {
        assertThrows(PatientQueryApiException.class, () -> {
            List<PatientStatus> patientStatusList = new ArrayList<>();
            patientStatusList.add(new PatientStatus());
            when((patientRepository).findByMasterPatientIdentifier(mpi)).thenReturn(Optional.of(createSrPatient()));
            when(patientStatusQueryService.getAllPatientStatus(mpi, facility)).thenReturn(patientStatusList);
            when((esutil).getDvaPatientFromPillars(mpi)).thenReturn(response);
            when(appConfig.getPatientCoverageUrl()).thenReturn("https://denodo6dev.davita.com/i_coverageall?patient_id={mpi}");
            when(restTemplate.getForObject(ArgumentMatchers.any(String.class), ArgumentMatchers.eq(Map.class), ArgumentMatchers.eq(mpi))).thenThrow(new RestClientException("test"));
            patientProfileQueryService.getPatientProfileDetails(mpi);
        });
    }

    @Test
    public void getPatientProfileStatusHistoryTest() {
        when(patientStatusRepository.findByMasterPatientIdentifier(mpi)).thenReturn(prepareStatuses());
        assertEquals(2, patientProfileQueryService.getPatientProfileStatusHistory(mpi, facility).size());
    }

    private SrPatient createSrPatient() {
        SrPatient srPatient = new SrPatient();
        srPatient.setCreateDateTimeGmt(DateUtils.currentTimestamp());
        srPatient.setDateOfBirth(Timestamp.valueOf(LocalDateTime.of(2019, Month.MAY, 16, 20, 20, 20)));

        SrPatientContact srPatientContact = new SrPatientContact();
        srPatientContact.setPrimaryFlag(Boolean.TRUE);
        srPatientContact.setContactTypeValue("12312321312");

        SrPatientLanguage patientLanguage = new SrPatientLanguage();
        patientLanguage.setLanguageTypeRecordNo(528);

        srPatient.setPatientContacts(Collections.singletonList(srPatientContact));
        srPatient.setPatientLanguages(Collections.singletonList(patientLanguage));
        return srPatient;
    }

    private PatientStatus createPatientStatus(String statusDisplayName) {
        PatientStatus patientStatus = new PatientStatus();
        patientStatus.setMasterPatientIdentifier(mpi);
        patientStatus.setFacilityNumber(facility);
        CodeConceptRefData readyToTreatStatusValue = new CodeConceptRefData();
        readyToTreatStatusValue.setDisplayName(statusDisplayName);
        patientStatus.setPatientStatusValue(readyToTreatStatusValue);
        patientStatus.setIsActive(true);
        patientStatus.setCreateDateTimeGmt(new Date());
        return patientStatus;
    }

    private Map<String, Object> createInnerMap(String insuranceBundleInd, String effectiveEndDate) {
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("coverage_effective_start_date", "2017-07-01T04:00:00+0000");
        innerMap.put("coverage_effective_end_date", effectiveEndDate);
        innerMap.put("insurance_bundle_ind", insuranceBundleInd);
        return innerMap;
    }
}
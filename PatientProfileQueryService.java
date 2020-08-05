package com.davita.cwow.patient.service;

import com.davita.cwow.model.domain.ref.codesets.CodeConceptRefData;
import com.davita.cwow.patient.service.api.config.ApplicationConfiguration;
import com.davita.cwow.patient.service.api.exception.PatientQueryApiException;
import com.davita.cwow.patient.service.enums.ContactTypeEnum;
import com.davita.cwow.patient.service.model.Patient;
import com.davita.cwow.patient.service.model.PatientContact;
import com.davita.cwow.patient.service.model.dto.AddressEntity;
import com.davita.cwow.patient.service.model.dto.EntityInfo;
import com.davita.cwow.patient.service.model.dto.PatientProfileDto;
import com.davita.cwow.patient.service.model.dto.PhoneEntity;
import com.davita.cwow.patient.service.model.dto.PillarAddress;
import com.davita.cwow.patient.service.model.status.PatientStatus;
import com.davita.cwow.patient.service.repository.SrPatientRepository;
import com.davita.cwow.patient.service.repository.SrPatientStatusRepository;
import com.davita.cwow.patient.service.util.ElasticSearchUtil;
import com.davita.cwow.patient.spanner.model.SrPatient;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrPatientConverter;
import com.davita.cwow.patient.spanner.model.converters.fromspanner.SrPatientStatusConverter;
import com.davita.cwow.patient.spanner.model.status.SrPatientStatus;
import com.davita.cwow.patient.spanner.util.DateTimeUtils;
import com.davita.cwow.pillars.model.Address;
import com.davita.cwow.pillars.model.Name;
import com.davita.cwow.pillars.model.Phone;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Component
@Service
public class PatientProfileQueryService {

    private static final Map<String, Integer> contactMap;

    @Autowired
    private SrPatientRepository patientRepository;

    @Autowired
    private SrPatientStatusConverter patientStatusConverter;

    @Autowired
    private SrPatientStatusRepository patientStatusRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ApplicationConfiguration appConfig;

    @Autowired
    private SrPatientConverter patientConverter;

    @Autowired
    private ElasticSearchUtil esutil;

    private static final int PRIMARY_LANG_RECORD_NUMBER = 528;
    private static final int SECONDARY_LANG_RECORD_NUMBER = 529;
    private static final String DENODO_ELEMENTS = "elements";
    private static final String DENODO_END_DATE = "coverage_effective_end_date";
    private static final String DENODO_START_DATE = "coverage_effective_start_date";
    private static final String DENODO_BUNDLE_FLAG_INDICATOR = "insurance_bundle_ind";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String YES = "Yes";
    private static final String NO = "No";
    private static final String FLAG_EXISTS = "Y";

    static {
        contactMap = new HashMap<>();
        contactMap.put("MOBILE", 0);
        contactMap.put("HOME", 1);
        contactMap.put("WORK", 2);
    }

    @Transactional
    public PatientProfileDto getPatientProfileDetails(String mpi) throws PatientQueryApiException {
        PatientProfileDto profileDto = new PatientProfileDto();
        Patient patient = new Patient();

        Optional<SrPatient> srPatientOpt = patientRepository.findByMasterPatientIdentifier(mpi);

        if (srPatientOpt.isPresent()) {
            log.info("Spanner patient for mpi - {} exists.", mpi);
            SrPatient srPatient = srPatientOpt.get();
            patient = patientConverter.convertWithContext(srPatient);

            log.info("Trying to fill in the profileDto by fields from spanner patient");

            fillPatientLanguages(profileDto, patient);
            fillPatientDtoFromSrPatient(profileDto, srPatient);

        } else {
            patient.setMasterPatientIdentifier(mpi);
        }

        fillPatientDtoStatus(profileDto, mpi);
        fillPatientCoverageInformation(profileDto, mpi);
        EntityInfo entityInfo = esutil.getDvaPatientFromPillars(mpi).getPatientInformation().getEntityInfo();
        if (Objects.nonNull(entityInfo)) {
            fillAddress(profileDto, entityInfo.getIsaddressof());
            fillContacts(profileDto, patient, entityInfo.getUsestelephone());
        }

        profileDto.setPatient(patient);
        return profileDto;
    }

    private void setAddress(AddressEntity addressEntity, List<Address> addressList) {
        PillarAddress pillarAddress = addressEntity.getAddress();
        Address address = new Address();

        Optional.ofNullable(pillarAddress).ifPresent(pa -> {
            address.setAddressLine1(pa.getAddressLine1Descr());
            address.setAddressLine2(pa.getAddressLine2Descr());
            address.setCityName(pa.getCityName());
            Optional.ofNullable(pa.getStateProvinceCode()).ifPresent(stateProvinceCode ->
                    address.setPostalCode(stateProvinceCode.getConceptName()));
            Optional.ofNullable(pa.getPostalCode()).ifPresent(postalCode ->
                    address.setPostalCode(postalCode.getConceptName()));
        });

        Optional.ofNullable(addressEntity.getContactAddressType()).ifPresent(addressType ->
                address.setAddressStatus(addressType.getConceptCode()));

        addressList.add(address);
    }

    private void setPhoneEntity(PhoneEntity entityPhone, Set<PatientContact> patientContactSet) {
        PatientContact contact = new PatientContact();
        CodeConceptRefData refData = new CodeConceptRefData();

        Optional.ofNullable(entityPhone.getContactPhoneType()).flatMap(contactPhoneType ->
                Optional.ofNullable(contactPhoneType.getConceptName())).ifPresent(conceptName -> {
            ContactTypeEnum contactType = ContactTypeEnum.fromValue(conceptName);
            refData.setDisplayName(contactType.getUiPhoneType());
        });

        contact.setContactType(refData);

        Phone phone = entityPhone.getPhone();
        Optional.ofNullable(phone).ifPresent(ph ->
                contact.setContactValue(ph.getPhoneNum()));

        patientContactSet.add(contact);
    }

    private void fillAddress(PatientProfileDto dto, List<AddressEntity> entityAddressList) {
        List<Address> addressList = new ArrayList<>();
        entityAddressList.forEach(addressEntity -> setAddress(addressEntity, addressList));
        dto.setPrimaryAddress(addressList);
    }

    private void fillContacts(PatientProfileDto dto, Patient patient, List<PhoneEntity> phoneEntityList) {
        Set<PatientContact> patientContactSet = new HashSet<>();
        phoneEntityList.forEach(phoneEntity -> setPhoneEntity(phoneEntity, patientContactSet));
        patient.setPatientContacts(patientContactSet);
        List<PatientContact> sortedPatientContactList = patientContactSet.stream().sorted(Comparator.comparingInt(
                contact -> contactMap.get(contact.getContactType().getDisplayName().toUpperCase(Locale.getDefault()))))
                .collect(Collectors.toList());
        dto.setSortedContactList(sortedPatientContactList);
    }

    private void fillPatientDtoStatus(PatientProfileDto profileDto, String mpi) {
        patientStatusRepository.findByMasterPatientIdentifier(mpi).stream()
                .filter(ps -> isNull(ps.getDeleteDateTimeGmt()))
                .max(Comparator.comparing(SrPatientStatus::getCreateDateTimeGmt))
                .ifPresent(status -> {
                    profileDto.setPatientStatus(patientStatusConverter.convertWithContext(status));
                });
    }

    private void fillPatientLanguages(PatientProfileDto profileDto, Patient patient) {
        if (patient.getIsTranslatorNeeded() != null)
            profileDto.setTranslatorText(patient.getIsTranslatorNeeded() ? "Yes" : "No");
        else {
            profileDto.setTranslatorText("Not yet documented");
        }
        if (CollectionUtils.isNotEmpty(patient.getPatientLanguages())) {
            patient.getPatientLanguages().stream().filter(pl -> pl.getDeleteDateTimeGmt() == null).forEach(pl -> {
                if (pl.getLanguageType().getRecordNumber().intValue() == PRIMARY_LANG_RECORD_NUMBER) {
                    profileDto.setPrimaryLanguage(pl);
                } else if (pl.getLanguageType().getRecordNumber().intValue() == SECONDARY_LANG_RECORD_NUMBER) {
                    profileDto.setSecondaryLanguage(pl);
                }
            });
        }
    }

    public List<PatientStatus> getPatientProfileStatusHistory(String mpi, String facilityId) {
        log.info("Fetching patient status' for {} from DB", mpi);
        return patientStatusConverter.convertCollectionToListWithContext(patientStatusRepository.findByMasterPatientIdentifier(mpi).stream()
                .filter(ps -> facilityId.equals(ps.getFacilityNumber()))
                .sorted(Comparator.comparing(SrPatientStatus::getCreateDateTimeGmt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList()));
    }

    private void fillPatientDtoFromSrPatient(PatientProfileDto profileDto, SrPatient srPatient) {
        Timestamp dateOfBirth = srPatient.getDateOfBirth();
        profileDto.setDob(String.valueOf(dateOfBirth));
        profileDto.setAge(calculateIntAge(dateOfBirth));

        profileDto.setEntityName(getNameFromSrPatient(srPatient));

        Optional.ofNullable(srPatient.getEthnicity()).ifPresent(profileDto::setEthnicity);
        Optional.ofNullable(srPatient.getGender()).ifPresent(profileDto::setAdministrativeGender);
    }

    private Name getNameFromSrPatient(SrPatient srPatient) {
        Name patientName = new Name();
        patientName.setFirstName(srPatient.getFirstName());
        patientName.setFamilyName(srPatient.getLastName());
        patientName.setMiddleName(srPatient.getMiddleName());
        patientName.setFullName(prepareFullName(srPatient));
        return patientName;
    }

    private String prepareFullName(SrPatient srPatient) {
        return srPatient.getFirstName() + " " + srPatient.getMiddleName() + " " + srPatient.getLastName();
    }

    private int calculateIntAge(Timestamp birthDate) {
        return Period.between(birthDate.toLocalDateTime().toLocalDate(), LocalDate.now()).getYears();
    }

    public String calculateAge(String birthDate) {
        String age = null;
        if (nonNull(birthDate)) {
            Date dobDate = DateTimeUtils.parseMdy(birthDate);
            if (nonNull(dobDate)) {
                age = String.valueOf(Period.between(
                        dobDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                        LocalDate.now()).getYears()
                );
            }
        }
        return age;
    }

    public String dateConverter(String date) {
        log.info("Date Converter" + date);

        Date modifiedDate = DateTimeUtils.parseYmd(date);
        String finalDate = DateTimeUtils.toStringMdy(modifiedDate);

        log.info("Converted Date" + finalDate);
        return finalDate;
    }

    /**
     * Populate Patient Coverage Information for specified mpi
     */
    private void fillPatientCoverageInformation(PatientProfileDto patientProfileDto, String masterPatientIdentifier)
            throws PatientQueryApiException {

        Map<String, Object> elementMap = getElementsFromDenodo(masterPatientIdentifier);
        if (nonNull(elementMap.get(DENODO_BUNDLE_FLAG_INDICATOR))) {
            patientProfileDto.setIsMedicareBundlePresent(
                    (FLAG_EXISTS).equals(elementMap.get(DENODO_BUNDLE_FLAG_INDICATOR)) ? YES : NO);

            if (YES.equals(patientProfileDto.getIsMedicareBundlePresent())) {
                patientProfileDto.setEffectiveStartDate(parseDate((String) elementMap.get(DENODO_START_DATE)));

                patientProfileDto.setEffectiveEndDate(parseDate((String) elementMap.get(DENODO_END_DATE)));
            }
        }
    }

    private ZonedDateTime parseDate(String date) {
        if (nonNull(date)) {
            return ZonedDateTime.parse(date, DateTimeFormatter.ofPattern(DATE_FORMAT));
        }
        return null;
    }

    /**
     * Get Response from Denodo for specified mpi
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getElementsFromDenodo(String masterPatientIdentifier) throws PatientQueryApiException {
        Map<String, Object> denodoResponseMap;
        try {
            denodoResponseMap = restTemplate.getForObject(appConfig.getPatientCoverageUrl(), Map.class,
                    masterPatientIdentifier);

        } catch (Exception exception) {
            log.error("Exception occured while fetching patient coverage information from denodo service: ", exception);
            throw new PatientQueryApiException(
                    "Exception occured while fetching patient coverage information from denodo service");
        }
        Map<String, Object> elementMap = new HashMap<>();
        if (Objects.nonNull(denodoResponseMap)) {
            List<Map<String, Object>> elementsList = (List<Map<String, Object>>) denodoResponseMap.get(DENODO_ELEMENTS);

            if (!elementsList.isEmpty()) {
                elementsList.sort(Comparator.comparing(
                        element -> nonNull(element.get(DENODO_END_DATE)) ? ZonedDateTime.parse(
                                (String) element.get(DENODO_END_DATE), DateTimeFormatter.ofPattern(DATE_FORMAT)) : null,
                        Comparator.nullsFirst(Comparator.reverseOrder())));
                elementMap = elementsList.get(0);
            }
        }
        return elementMap;
    }
}
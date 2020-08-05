package com.davita.cwow.patient.spanner.model.converters.tospanner;

import com.davita.cwow.model.domain.ref.codesets.CodeConceptRefData;
import com.davita.cwow.patient.service.model.schedule.TreatingElsewhere;
import com.davita.cwow.patient.service.model.schedule.dto.TreatingElsewhereDto;
import com.davita.cwow.patient.spanner.model.converters.TestUtil;
import com.davita.cwow.patient.spanner.model.schedule.SrScheduleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TreatingElsewhereConverterTest {

    @InjectMocks
    private TreatingElsewhereConverter converter;

    @Test
    void convert() {
        TreatingElsewhere treatingElsewhere = buildTreatingElsewhereDto();
        SrScheduleException srScheduleException = converter.convert(treatingElsewhere);

        assertTreatingElsewhere(treatingElsewhere, srScheduleException);
      }

    @Test
    void convertFromDto() {
        TreatingElsewhereDto treatingElsewhereDto = buildTreatingElsewhereDto();
        assertTreatingElsewhere(treatingElsewhereDto, converter.convert(treatingElsewhereDto));
    }

    private void assertTreatingElsewhere (TreatingElsewhere treatingElsewhere, SrScheduleException srScheduleException) {
        assertEquals(treatingElsewhere.getLocationName(), srScheduleException.getLocationName());
        assertEquals(treatingElsewhere.getReasonTxt(), srScheduleException.getReasonTxt());
        TestUtil.assertCodeConceptRefData(treatingElsewhere.getReason(), srScheduleException.getReasonCodeId(), srScheduleException.getReasonDisplay());
    }

    private TreatingElsewhereDto buildTreatingElsewhereDto() {
        TreatingElsewhereDto treatingElsewhereDto = new TreatingElsewhereDto();
        treatingElsewhereDto.setMasterPatientIdentifier("123456");
        treatingElsewhereDto.setLocationName("locationName");
        treatingElsewhereDto.setReason(CodeConceptRefData.builder()
                .codeId("codeId")
                .displayName("displayName")
                .build());
         return treatingElsewhereDto;
    }
}
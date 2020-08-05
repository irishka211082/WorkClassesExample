package com.davita.cwow.patient.spanner.model.converters.tospanner;

import com.davita.cwow.patient.service.model.schedule.TreatingElsewhere;
import com.davita.cwow.patient.service.model.schedule.dto.TreatingElsewhereDto;
import com.davita.cwow.patient.spanner.model.schedule.SrScheduleException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;


@Component
public class TreatingElsewhereConverter extends ScheduleExceptionToSpannerConverter<TreatingElsewhere> {

    @Override
    public SrScheduleException convert(TreatingElsewhere treatingElsewhere) {
        if (Objects.isNull(treatingElsewhere)) {
            return null;
        }
        SrScheduleException srScheduleException = new SrScheduleException();
        Optional.ofNullable(treatingElsewhere.getReason()).ifPresent((reason) -> {
            srScheduleException.setReasonCodeId(reason.getCodeId());
            srScheduleException.setReasonDisplay(reason.getDisplayName());
        });
        srScheduleException.setReasonTxt(treatingElsewhere.getReasonTxt());
        srScheduleException.setLocationName(treatingElsewhere.getLocationName());
        convertCommonFields(treatingElsewhere, srScheduleException);
        return srScheduleException;
    }
}

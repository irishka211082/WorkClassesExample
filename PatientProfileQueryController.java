package com.davita.cwow.patient.service.api.query;

import com.davita.cwow.patient.service.PatientProfileQueryService;
import com.davita.cwow.patient.service.api.exception.PatientQueryApiException;
import com.davita.cwow.patient.service.model.dto.PatientProfileDto;
import com.davita.cwow.patient.service.model.status.PatientStatus;
import com.davita.cwow.pillars.model.DvaPatientMaster;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2/patients/{mpi}/profile")
@Api(value = "/v2/patients/{mpi}/profile", tags = { "05 - Patient profile"})
public class PatientProfileQueryController {

		@Autowired
		private PatientProfileQueryService patientProfileQueryService;

		@ApiOperation(value = "Get Patient Profile data from Reggie present in ES Patient Pillar and phone number in CWOW for the specified mpi", notes = "Returns Patient Profile information from Reggie and CWOW(phone numbers)", response = Map.class)
		@GetMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
		@ApiResponses(value = { @ApiResponse(code = 200, message = "Success", response = PatientProfileDto.class),
				@ApiResponse(code = 401, message = "Unauthorized"),
				@ApiResponse(code = 500, message = "Internal Server Error", response = PatientQueryApiException.class) })
		public ResponseEntity<PatientProfileDto> getPatientProfile(
				@ApiParam(value = "Master Patient Identifier - For eg: 1740099", required = true) @PathVariable String mpi,
				@RequestHeader(value = "facilityId") String facilityId) throws PatientQueryApiException {
			return ResponseEntity.ok(patientProfileQueryService.getPatientProfileDetails(mpi));
		}
		
		@ApiOperation(value = "Get Patient Status History from source", notes = "Returns Patient Status History", response = PatientStatus.class, responseContainer="List")
		@GetMapping(path = "/status",produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
		@ApiResponses(value = { @ApiResponse(code = 200, message = "Success", response = PatientStatus.class, responseContainer="List"),
				@ApiResponse(code = 401, message = "Unauthorized"),
				@ApiResponse(code = 500, message = "Internal Server Error", response = PatientQueryApiException.class) })
		public ResponseEntity<List<PatientStatus>> getPatientProfileStatusHistory(
				@ApiParam(value = "Master Patient Identifier - For eg: 1740099", required = true) @PathVariable String mpi,
				@RequestHeader(value = "facilityId") String facilityId) throws PatientQueryApiException {
			return ResponseEntity.ok(patientProfileQueryService.getPatientProfileStatusHistory(mpi, facilityId));		
		}
		
		@ApiOperation(value = "Create DvaPatientMaster Data", notes = "Create DvaPatientMaster Data")
		@ApiResponses(value = {
				@ApiResponse(code = 200, message = "Success", response = DvaPatientMaster.class, responseContainer = "Object"),
				@ApiResponse(code = 401, message = "Unauthorized"),
				@ApiResponse(code = 500, message = "Internal server error if patient is not found", response = PatientQueryApiException.class) })
		@PostMapping(path = "/DvaPatientMaster", consumes = MediaType.APPLICATION_JSON_UTF8_VALUE, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
		public ResponseEntity<Object> createDvaPatientMaster(
				@ApiParam(name = "mpi", required = true) @PathVariable String mpi) {
			return new ResponseEntity<>("CREATED", HttpStatus.CREATED);
		}
}
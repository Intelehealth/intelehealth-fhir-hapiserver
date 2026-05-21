package ca.uhn.fhir.jpa.starter.mdm;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.mdm.api.MdmConstants;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoldenResourceCrIdServiceTest {

	@Mock
	DaoRegistry myDaoRegistry;

	@Mock
	IFhirResourceDao<Patient> myPatientDao;

	@Mock
	CrIdGenerator myCrIdGenerator;

	private GoldenResourceCrIdService mySvc;

	@BeforeEach
	void setUp() {
		when(myDaoRegistry.getResourceDao(Patient.class)).thenReturn(myPatientDao);
		mySvc = new GoldenResourceCrIdService(myDaoRegistry, myCrIdGenerator);
	}

	@Test
	void replacesDefaultGoldenIdentifierWithCustomCrId() {
		Patient patient = goldenPatient("Patient/1002");
		patient.addIdentifier()
				.setSystem(MdmConstants.HAPI_ENTERPRISE_IDENTIFIER_SYSTEM)
				.setValue("9cfb60b7-cdf2-48e7-97dc-2740e78e6c96");
		when(myPatientDao.read(eq(new org.hl7.fhir.r4.model.IdType("Patient", "1002")), any()))
				.thenReturn(patient);
		when(myCrIdGenerator.nextCrId()).thenReturn("CR-2026-000001");

		mySvc.ensureCustomCrId("Patient/1002", null);

		verify(myPatientDao).update(eq(patient), any(RequestDetails.class));
		assertEquals(1, patient.getIdentifier().size());
		assertEquals(GoldenResourceCrIdService.CR_ID_SYSTEM, patient.getIdentifierFirstRep().getSystem());
		assertEquals("CR-2026-000001", patient.getIdentifierFirstRep().getValue());
	}

	@Test
	void removesDefaultIdentifierWhenCustomCrIdAlreadyExists() {
		Patient patient = goldenPatient("Patient/1002");
		patient.addIdentifier()
				.setSystem(MdmConstants.HAPI_ENTERPRISE_IDENTIFIER_SYSTEM)
				.setValue("uuid-value");
		patient.addIdentifier()
				.setSystem(GoldenResourceCrIdService.CR_ID_SYSTEM)
				.setValue("CR-2026-000001");
		when(myPatientDao.read(eq(new org.hl7.fhir.r4.model.IdType("Patient", "1002")), any()))
				.thenReturn(patient);

		mySvc.ensureCustomCrId("Patient/1002", null);

		verify(myPatientDao).update(eq(patient), any(RequestDetails.class));
		verify(myCrIdGenerator, never()).nextCrId();
		assertEquals(1, patient.getIdentifier().size());
		assertEquals(GoldenResourceCrIdService.CR_ID_SYSTEM, patient.getIdentifierFirstRep().getSystem());
	}

	@Test
	void skipsSourcePatients() {
		Patient patient = new Patient();
		patient.setId("Patient/1003");
		when(myPatientDao.read(eq(new org.hl7.fhir.r4.model.IdType("Patient", "1003")), any()))
				.thenReturn(patient);

		mySvc.ensureCustomCrId("Patient/1003", null);

		verify(myPatientDao, never()).update(any(), any(RequestDetails.class));
		assertTrue(patient.getIdentifier().isEmpty());
	}

	private Patient goldenPatient(String theId) {
		Patient patient = new Patient();
		patient.setId(theId);
		patient.getMeta()
				.addTag()
				.setSystem(MdmConstants.SYSTEM_MDM_MANAGED)
				.setCode(MdmConstants.CODE_HAPI_MDM_MANAGED);
		return patient;
	}
}

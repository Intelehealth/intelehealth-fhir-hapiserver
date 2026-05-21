package ca.uhn.fhir.jpa.starter.mdm;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.mdm.api.MdmConstants;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "hapi.fhir", name = {"mdm_enabled", "custom_cr_id_enabled"}, havingValue = "true")
public class GoldenResourceCrIdService {

	private static final Logger ourLog = LoggerFactory.getLogger(GoldenResourceCrIdService.class);
	static final String CR_ID_SYSTEM = "urn:intelehealth:cruid";

	private final IFhirResourceDao<Patient> myPatientDao;
	private final CrIdGenerator myCrIdGenerator;

	public GoldenResourceCrIdService(DaoRegistry theDaoRegistry, CrIdGenerator theCrIdGenerator) {
		myPatientDao = theDaoRegistry.getResourceDao(Patient.class);
		myCrIdGenerator = theCrIdGenerator;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void backfillExistingGoldenPatients() {
		SystemRequestDetails requestDetails = SystemRequestDetails.newSystemRequestAllPartitions();
		SearchParameterMap searchParameterMap = SearchParameterMap.newSynchronous()
				.add("_tag", new TokenParam(MdmConstants.SYSTEM_MDM_MANAGED, MdmConstants.CODE_HAPI_MDM_MANAGED));

		List<Patient> goldenPatients = myPatientDao.searchForResources(searchParameterMap, requestDetails);
		ourLog.info("Backfilling custom CR IDs for {} golden patient(s)", goldenPatients.size());
		for (Patient patient : goldenPatients) {
			ensureCustomCrId(patient, requestDetails);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void ensureCustomCrId(String theGoldenResourceId, RequestDetails theRequestDetails) {
		if (theGoldenResourceId == null || theGoldenResourceId.isBlank()) {
			ourLog.debug("Skipping CR ID assignment because golden resource ID is blank");
			return;
		}

		IdType goldenId = normalizeGoldenId(theGoldenResourceId);
		if (goldenId == null) {
			ourLog.debug("Skipping CR ID assignment because golden resource ID {} is not a Patient", theGoldenResourceId);
			return;
		}

		Patient patient = myPatientDao.read(goldenId, toSystemRequestDetails(theRequestDetails));
		if (patient != null) {
			ourLog.info("Loaded golden patient {} for CR ID evaluation", patient.getIdElement().toUnqualifiedVersionless().getValue());
			ensureCustomCrId(patient, theRequestDetails);
		} else {
			ourLog.warn("Could not load golden patient {}", goldenId.getValue());
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void ensureCustomCrId(Patient thePatient, RequestDetails theRequestDetails) {
		if (thePatient == null || !isGoldenPatient(thePatient)) {
			ourLog.debug("Skipping CR ID assignment because resource is not an MDM golden patient");
			return;
		}

		boolean removedDefaultIdentifier = thePatient.getIdentifier().removeIf(this::isDefaultGoldenIdentifier);
		boolean hasCustomCrId = hasCustomCrId(thePatient);
		String patientId = thePatient.getIdElement().toUnqualifiedVersionless().getValue();

		if (!hasCustomCrId) {
			String crId = myCrIdGenerator.nextCrId();
			ourLog.info("Assigning custom CR ID {} to golden patient {}", crId, patientId);
			thePatient.addIdentifier(new Identifier().setSystem(CR_ID_SYSTEM).setValue(crId));
		} else {
			ourLog.info("Golden patient {} already has custom CR ID {}", patientId, existingCustomCrId(thePatient));
		}

		if (removedDefaultIdentifier || !hasCustomCrId) {
			if (removedDefaultIdentifier) {
				ourLog.info("Removed default HAPI enterprise identifier from golden patient {}", patientId);
			}
			ourLog.info("Updating golden patient {} with custom CR ID identifiers", patientId);
			myPatientDao.update(thePatient, toSystemRequestDetails(theRequestDetails));
		} else {
			ourLog.debug("No identifier update required for golden patient {}", patientId);
		}
	}

	private IdType normalizeGoldenId(String theGoldenResourceId) {
		IdType id = new IdType(theGoldenResourceId);
		String resourceType = id.getResourceType();
		if (resourceType != null && !"Patient".equals(resourceType)) {
			return null;
		}
		return new IdType("Patient", id.getIdPart());
	}

	private SystemRequestDetails toSystemRequestDetails(RequestDetails theRequestDetails) {
		if (theRequestDetails instanceof SystemRequestDetails systemRequestDetails) {
			return new SystemRequestDetails(systemRequestDetails);
		}
		if (theRequestDetails != null) {
			return new SystemRequestDetails(theRequestDetails);
		}
		return SystemRequestDetails.newSystemRequestAllPartitions();
	}

	private boolean isDefaultGoldenIdentifier(Identifier theIdentifier) {
		return MdmConstants.HAPI_ENTERPRISE_IDENTIFIER_SYSTEM.equals(theIdentifier.getSystem());
	}

	static boolean hasCustomCrId(Patient thePatient) {
		return thePatient.getIdentifier().stream()
				.anyMatch(identifier -> CR_ID_SYSTEM.equals(identifier.getSystem()) && identifier.hasValue());
	}

	private static String existingCustomCrId(Patient thePatient) {
		return thePatient.getIdentifier().stream()
				.filter(identifier -> CR_ID_SYSTEM.equals(identifier.getSystem()) && identifier.hasValue())
				.map(Identifier::getValue)
				.findFirst()
				.orElse("<missing>");
	}

	static boolean isGoldenPatient(Patient thePatient) {
		return thePatient.getMeta().getTag().stream()
				.anyMatch(tag -> MdmConstants.SYSTEM_MDM_MANAGED.equals(tag.getSystem())
						&& MdmConstants.CODE_HAPI_MDM_MANAGED.equals(tag.getCode()));
	}
}

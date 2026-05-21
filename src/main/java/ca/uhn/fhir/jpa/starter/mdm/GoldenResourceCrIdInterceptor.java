package ca.uhn.fhir.jpa.starter.mdm;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.mdm.interceptor.IMdmStorageInterceptor;
import ca.uhn.fhir.mdm.model.mdmevents.MdmLinkEvent;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Interceptor
@ConditionalOnProperty(prefix = "hapi.fhir", name = {"mdm_enabled", "custom_cr_id_enabled"}, havingValue = "true")
public class GoldenResourceCrIdInterceptor implements IMdmStorageInterceptor {

	private static final Logger ourLog = LoggerFactory.getLogger(GoldenResourceCrIdInterceptor.class);

	private final GoldenResourceCrIdService myGoldenResourceCrIdService;

	public GoldenResourceCrIdInterceptor(GoldenResourceCrIdService theGoldenResourceCrIdService) {
		myGoldenResourceCrIdService = theGoldenResourceCrIdService;
	}

	@Hook(Pointcut.MDM_POST_CREATE_LINK)
	public void assignEnterpriseCrIdOnCreate(RequestDetails theRequestDetails, MdmLinkEvent theMdmLinkEvent) {
		if (theMdmLinkEvent == null) {
			ourLog.debug("Skipping CR ID assignment because MDM link event is null");
			return;
		}

		ourLog.info("Received MDM_POST_CREATE_LINK event for {} link(s)", theMdmLinkEvent.getMdmLinks().size());
		theMdmLinkEvent.getMdmLinks().stream()
				.map(link -> link.getGoldenResourceId())
				.forEach(goldenId -> {
					ourLog.info("Ensuring custom CR ID for golden resource {}", goldenId);
					myGoldenResourceCrIdService.ensureCustomCrId(goldenId, theRequestDetails);
				});
	}

	@Hook(Pointcut.MDM_POST_UPDATE_LINK)
	public void assignEnterpriseCrIdOnUpdate(RequestDetails theRequestDetails, MdmLinkEvent theMdmLinkEvent) {
		ourLog.info("Received MDM_POST_UPDATE_LINK event");
		assignEnterpriseCrIdOnCreate(theRequestDetails, theMdmLinkEvent);
	}
}

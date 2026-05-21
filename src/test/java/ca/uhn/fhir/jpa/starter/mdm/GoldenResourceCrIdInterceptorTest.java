package ca.uhn.fhir.jpa.starter.mdm;

import ca.uhn.fhir.mdm.model.mdmevents.MdmLinkEvent;
import ca.uhn.fhir.mdm.model.mdmevents.MdmLinkJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GoldenResourceCrIdInterceptorTest {

	@Mock
	GoldenResourceCrIdService myGoldenResourceCrIdService;

	@Test
	void delegatesGoldenIdFromCreateEvent() {
		GoldenResourceCrIdInterceptor interceptor = new GoldenResourceCrIdInterceptor(myGoldenResourceCrIdService);
		MdmLinkEvent linkEvent = new MdmLinkEvent()
				.addMdmLink(new MdmLinkJson().setGoldenResourceId("Patient/1002"));

		interceptor.assignEnterpriseCrIdOnCreate(null, linkEvent);

		verify(myGoldenResourceCrIdService).ensureCustomCrId("Patient/1002", null);
	}

	@Test
	void ignoresEmptyEvents() {
		GoldenResourceCrIdInterceptor interceptor = new GoldenResourceCrIdInterceptor(myGoldenResourceCrIdService);

		interceptor.assignEnterpriseCrIdOnCreate(null, null);

		verifyNoInteractions(myGoldenResourceCrIdService);
	}
}

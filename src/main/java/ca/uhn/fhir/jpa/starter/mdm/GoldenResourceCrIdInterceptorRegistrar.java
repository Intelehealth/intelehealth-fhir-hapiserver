package ca.uhn.fhir.jpa.starter.mdm;

import ca.uhn.fhir.IHapiBootOrder;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hapi.fhir", name = {"mdm_enabled", "custom_cr_id_enabled"}, havingValue = "true")
public class GoldenResourceCrIdInterceptorRegistrar {

	private static final Logger ourLog = LoggerFactory.getLogger(GoldenResourceCrIdInterceptorRegistrar.class);

	private final IInterceptorService myInterceptorService;
	private final GoldenResourceCrIdInterceptor myGoldenResourceCrIdInterceptor;

	public GoldenResourceCrIdInterceptorRegistrar(
			IInterceptorService theInterceptorService, GoldenResourceCrIdInterceptor theGoldenResourceCrIdInterceptor) {
		myInterceptorService = theInterceptorService;
		myGoldenResourceCrIdInterceptor = theGoldenResourceCrIdInterceptor;
	}

	@EventListener(classes = {ContextRefreshedEvent.class})
	@Order(IHapiBootOrder.REGISTER_INTERCEPTORS)
	public void register() {
		ourLog.info("Registering GoldenResourceCrIdInterceptor with JPA interceptor service");
		myInterceptorService.registerInterceptor(myGoldenResourceCrIdInterceptor);
	}
}

package ca.uhn.fhir.jpa.starter.mdm;

import ca.uhn.fhir.interceptor.api.IInterceptorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoldenResourceCrIdInterceptorRegistrarTest {

	@Mock
	IInterceptorService myInterceptorService;

	@Mock
	GoldenResourceCrIdInterceptor myGoldenResourceCrIdInterceptor;

	@Test
	void registersInterceptorWithJpaInterceptorService() {
		GoldenResourceCrIdInterceptorRegistrar registrar =
				new GoldenResourceCrIdInterceptorRegistrar(myInterceptorService, myGoldenResourceCrIdInterceptor);

		registrar.register();

		verify(myInterceptorService).registerInterceptor(myGoldenResourceCrIdInterceptor);
	}
}

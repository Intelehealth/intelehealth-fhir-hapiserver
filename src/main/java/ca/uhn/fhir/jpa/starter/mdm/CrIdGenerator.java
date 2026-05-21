package ca.uhn.fhir.jpa.starter.mdm;

@FunctionalInterface
public interface CrIdGenerator {

	String nextCrId();
}

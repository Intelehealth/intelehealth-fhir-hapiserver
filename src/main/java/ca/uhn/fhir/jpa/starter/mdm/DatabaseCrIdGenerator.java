package ca.uhn.fhir.jpa.starter.mdm;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Year;
import java.util.Locale;

@Service
@ConditionalOnProperty(prefix = "hapi.fhir", name = {"mdm_enabled", "custom_cr_id_enabled"}, havingValue = "true")
public class DatabaseCrIdGenerator implements CrIdGenerator {

	private static final String CREATE_TABLE_SQL =
			"CREATE TABLE IF NOT EXISTS cr_id_sequence (" +
					"sequence_year INTEGER PRIMARY KEY, " +
					"next_value BIGINT NOT NULL" +
					")";
	private static final String SELECT_FOR_UPDATE_SQL =
			"SELECT next_value FROM cr_id_sequence WHERE sequence_year = ? FOR UPDATE";
	private static final String INSERT_SQL =
			"INSERT INTO cr_id_sequence(sequence_year, next_value) VALUES (?, ?)";
	private static final String UPDATE_SQL =
			"UPDATE cr_id_sequence SET next_value = ? WHERE sequence_year = ?";

	private final JdbcTemplate myJdbcTemplate;
	private final TransactionTemplate myTransactionTemplate;

	public DatabaseCrIdGenerator(
			JdbcTemplate theJdbcTemplate, PlatformTransactionManager theTransactionManager) {
		myJdbcTemplate = theJdbcTemplate;
		myTransactionTemplate = new TransactionTemplate(theTransactionManager);
		myTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@PostConstruct
	void ensureSequenceTableExists() {
		myJdbcTemplate.execute(CREATE_TABLE_SQL);
	}

	@Override
	public String nextCrId() {
		int currentYear = Year.now().getValue();
		Long nextSequence = myTransactionTemplate.execute(status -> nextSequenceForYear(currentYear));
		if (nextSequence == null) {
			throw new IllegalStateException("Unable to generate the next CR identifier");
		}
		return formatCrId(currentYear, nextSequence);
	}

	static String formatCrId(int theYear, long theSequence) {
		return String.format(Locale.ROOT, "CR-%04d-%06d", theYear, theSequence);
	}

	private long nextSequenceForYear(int theYear) {
		while (true) {
			Long nextValue = myJdbcTemplate.query(
					SELECT_FOR_UPDATE_SQL,
					resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
					theYear);

			if (nextValue != null) {
				myJdbcTemplate.update(UPDATE_SQL, nextValue + 1, theYear);
				return nextValue;
			}

			try {
				myJdbcTemplate.update(INSERT_SQL, theYear, 2L);
				return 1L;
			} catch (DuplicateKeyException e) {
				// A concurrent request created this year's counter first. Retry inside the same transaction.
			}
		}
	}
}

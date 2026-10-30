package ee.revkassa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Skeem tuleb failidest docs/database/1_reset_database.sql + 2_create.sql (käivitatakse käsitsi).
@SpringBootTest
class DatabaseSchemaTests {

	private static final List<String> TABLES = List.of(
			"member", "product", "purchase", "purchase_line", "payment", "member_favourite",
			"account_entry", "stock_movement", "membership_fee", "membership_charge", "invoice",
			"membership_level", "event", "event_level_fee", "event_participant", "event_budget_line");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void allTablesExist() {
		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE'",
				String.class);

		assertThat(tables).containsExactlyInAnyOrderElementsOf(TABLES);
	}

	@Test
	void fiveMembershipLevelsAreCreatedWithSchema() {
		List<String> levels = jdbcTemplate.queryForList(
				"select name from membership_level order by sort_order", String.class);

		assertThat(levels).startsWith("Täisliige", "Noorliige", "Vilistlane", "Toetajaliige", "Auliige");
	}

}

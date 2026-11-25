package ee.revkassa.infrastructure.config;

import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Every time in an API response is written in the club's time zone (Europe/Tallinn, +02:00 or +03:00 in summer).
 * Times read from PostgreSQL come back in UTC, times from the Clock in Tallinn time: without this the same moment
 * would reach the frontend in two different forms.
 */
@JacksonComponent
public class OffsetDateTimeSerializer extends ValueSerializer<OffsetDateTime> {

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator generator, SerializationContext context) {
        OffsetDateTime inAppZone = value.atZoneSameInstant(ClockConfig.APP_ZONE).toOffsetDateTime();
        generator.writeString(inAppZone.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}

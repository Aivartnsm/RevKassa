package ee.revkassa.persistence.eventlevelfee;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class EventLevelFeeId implements Serializable {

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "level_id", nullable = false)
    private Long levelId;
}

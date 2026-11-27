package ee.revkassa.persistence.eventparticipant;

/**
 * How many active participants joined at one membership level (the level copied when they joined).
 */
public record LevelCount(String levelName, long participantCount) {
}

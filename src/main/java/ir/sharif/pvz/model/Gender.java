package ir.sharif.pvz.model;

/**
 * Gender of a registered user.
 */
public enum Gender {
    MALE,
    FEMALE;

    /**
     * Parses a raw command argument into a gender, or returns null if invalid.
     */
    public static Gender fromString(String raw) {
        for (Gender gender : values()) {
            if (gender.name().equalsIgnoreCase(raw)) {
                return gender;
            }
        }
        return null;
    }
}

package app.helper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Utility class to generate codes using PostgreSQL sequences.
 */
@Component
public class CodeGenerator {

    private static JdbcTemplate staticJdbcTemplate;

    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        CodeGenerator.staticJdbcTemplate = jdbcTemplate;
    }

    /**
     * Generate a code using a given sequence name and optional prefix.
     *
     * @param sequenceName the name of the PostgreSQL sequence (must exist)
     * @param prefix       optional prefix (e.g., "P" for patient)
     * @param padding      number of digits to pad the sequence value with
     * @return formatted code, e.g., "P000123"
     */
    public static String generateCode(String sequenceName, String prefix, int padding) {
        if (staticJdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }

        Long nextVal = staticJdbcTemplate.queryForObject("SELECT nextval(?)", Long.class, sequenceName);
        return String.format("%s%0" + padding + "d", prefix != null ? prefix : "", nextVal);
    }
}

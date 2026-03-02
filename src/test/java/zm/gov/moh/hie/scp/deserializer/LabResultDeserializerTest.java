package zm.gov.moh.hie.scp.deserializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zm.gov.moh.hie.scp.dto.LabResult;
import zm.gov.moh.hie.scp.dto.Observation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LabResultDeserializerTest {

    private LabResultDeserializer deserializer;

    @BeforeEach
    void setUp() {
        deserializer = new LabResultDeserializer();
    }

    /**
     * Test null input handling
     */
    @Test
    void testNullInput() {
        LabResult result = deserializer.deserialize(null);
        assertNull(result, "Should return null for null input");
    }

    /**
     * Test empty byte array
     */
    @Test
    void testEmptyByteArray() {
        LabResult result = deserializer.deserialize(new byte[0]);
        assertNull(result, "Should return null for empty byte array");
    }

    /**
     * Test invalid HL7 message
     */
    @Test
    void testInvalidHL7Message() {
        String invalidMessage = "This is not a valid HL7 message";
        LabResult result = deserializer.deserialize(invalidMessage.getBytes());
        assertNull(result, "Should return null for invalid HL7 message");
    }

    /**
     * Test isEndOfStream method
     */
    @Test
    void testIsEndOfStream() {
        boolean isEndOfStream = deserializer.isEndOfStream(null);
        assertFalse(isEndOfStream, "isEndOfStream should always return false");
    }

    /**
     * Test getProducedType method
     */
    @Test
    void testGetProducedType() {
        assertNotNull(deserializer.getProducedType());
    }

}

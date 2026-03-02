package zm.gov.moh.hie.scp.deserializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zm.gov.moh.hie.scp.dto.LabResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LabResultDeserializer
 *
 * Tests core functionality including:
 * - Null input handling
 * - Invalid message handling
 * - isEndOfStream behavior
 * - Type information
 */
class LabResultDeserializerTest {

    private LabResultDeserializer deserializer;

    @BeforeEach
    void setUp() {
        deserializer = new LabResultDeserializer();
    }

    /**
     * Test that null input returns null
     */
    @Test
    void testNullInput() {
        LabResult result = deserializer.deserialize(null);
        assertNull(result, "Deserializer should return null for null input");
    }

    /**
     * Test that empty byte array returns null
     */
    @Test
    void testEmptyByteArray() {
        LabResult result = deserializer.deserialize(new byte[0]);
        assertNull(result, "Deserializer should return null for empty byte array");
    }

    /**
     * Test that invalid HL7 message returns null
     */
    @Test
    void testInvalidHL7Message() {
        String invalidMessage = "This is not a valid HL7 message at all";
        LabResult result = deserializer.deserialize(invalidMessage.getBytes());
        assertNull(result, "Deserializer should return null for invalid HL7 message");
    }

    /**
     * Test that random bytes return null
     */
    @Test
    void testRandomBytes() {
        byte[] randomBytes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        LabResult result = deserializer.deserialize(randomBytes);
        assertNull(result, "Deserializer should return null for random bytes");
    }

    /**
     * Test isEndOfStream always returns false
     */
    @Test
    void testIsEndOfStream() {
        boolean result = deserializer.isEndOfStream(null);
        assertFalse(result, "isEndOfStream should always return false");
    }

    /**
     * Test getProducedType is not null
     */
    @Test
    void testGetProducedType() {
        assertNotNull(deserializer.getProducedType(), "getProducedType should return non-null TypeInformation");
        assertEquals("LabResult", deserializer.getProducedType().getTypeClass().getSimpleName(),
                "Produced type should be LabResult");
    }

    /**
     * Test message with missing MSH segment
     */
    @Test
    void testMissingMSHSegment() {
        String message = "PID|||12345^^^HOSPITAL||DOE^JOHN||19700101|M\r";
        LabResult result = deserializer.deserialize(message.getBytes());
        assertNull(result, "Should return null when MSH segment is missing");
    }

    /**
     * Test sanitize functionality with newlines
     */
    @Test
    void testSanitizationWithNewlines() {
        // Message with LF newlines instead of CR
        String message = "MSH|^~\\&|TEST|001|TEST|002|20250101000000||ORU^R01|TEST|P|2.5\n" +
                "Not a valid HL7 structure";

        // Should not throw exception, just return null
        LabResult result = deserializer.deserialize(message.getBytes());
        assertNull(result);
    }

    /**
     * Test exception handling - malformed message that would cause parsing error
     */
    @Test
    void testExceptionHandling() {
        String message = "MSH|^~\\&|";  // Incomplete message

        // Should catch exception and return null, not throw
        LabResult result = assertDoesNotThrow(
            () -> deserializer.deserialize(message.getBytes()),
            "Deserializer should handle exceptions gracefully"
        );
        assertNull(result);
    }

    /**
     * Test that deserializer doesn't modify input
     */
    @Test
    void testInputNotModified() {
        String message = "Invalid message";
        byte[] input = message.getBytes();
        byte[] original = input.clone();

        deserializer.deserialize(input);

        assertArrayEquals(original, input, "Input byte array should not be modified");
    }

    /**
     * Test multiple calls with different inputs
     */
    @Test
    void testMultipleCalls() {
        // First call
        LabResult result1 = deserializer.deserialize(null);
        assertNull(result1);

        // Second call
        LabResult result2 = deserializer.deserialize(new byte[0]);
        assertNull(result2);

        // Third call
        LabResult result3 = deserializer.deserialize("invalid".getBytes());
        assertNull(result3);

        // Deserializer should be reusable
        LabResult result4 = deserializer.deserialize(null);
        assertNull(result4);
    }

    /**
     * Test with real HL7 message from Disa Lab
     */
    @Test
    void testRealDisaLabMessage() {
        String realMessage = "MSH|^~\\&|DISA*LAB|New Chinsali General Hospital^ZCD^URI|SmartCare|3551|20260302111242||ORU^R01^ORU_R01|05C27E60-31CA-4B21-B462-F470BFB6C7D1|P^T|2.5||||AL|ZMB\r" +
                "SFT|Laboratory System Technologies Pty (Ltd)|05.05.03.1323|DisaHL7Rslt||Disa*Lab|20230220|\r" +
                "PID|1||5502-0002P-01432-0^^^^MR||CHANDA^DENNIS^D^||19840910|M|||||000000000|||||||||||||||||N||\r" +
                "NTE|1|L|SPECDT:2026022610:59 F RCVDT:0000000000:00 F Priority:R|\r" +
                "NTE|2|L|REGBY:MM1 F 2026022715:01:03 F MODBY: F 0000000000:00|\r" +
                "PV1|1|U|^^^55020002^^^^^|||||^Mbanga^|||||||||||||||||||||||||||||||||||||\r" +
                "ORC|SC|20EBADEF4A|ZCD0186366||CM|||||||||||||||||||||||||||\r" +
                "OBR|1|20EBADEF4A|ZCD0186366|47245-6^VIRAL^LN^VIRAL^VIRAL^L|||202602271501|||||^||202602271501|B^^Blood|^Mbanga||||||202602271501|||F|||||||202602271501||202602271501\r" +
                "OBX|1|ST| ||VIRAL||||||F\r" +
                "OBX|2|DT|NO LOINC^ART Start Date^LN^VDATE^ART Start Date^L||19000101||0-0||||F|||202602271501|^ZCD|||\r" +
                "OBR|2|20EBADEF4A|ZCD0186366|20447-9^HIVVL^LN^HIVVL^HIVVL^L|||202603021000|||||^||202602271501|B^^Blood|^Mbanga||||||202603021012|||F|||||||202603021012||202603021012\r" +
                "OBX|1|ST| ||HIVVL HIV VIRAL LOAD||||||F\r" +
                "OBX|2|CE|20447-9^HIV : Viral Load (Low value)^LN^HIVVC^HIV : Viral Load (Low value)^L||Target Not Detected||0-0||||F|||202603021000|^ZCD|||PNTHR\r";

        System.out.println("[DEBUG TEST] Testing real Disa Lab message...");
        System.out.println("[DEBUG TEST] Message length: " + realMessage.length() + " bytes");

        LabResult result = deserializer.deserialize(realMessage.getBytes());

        if (result != null) {
            System.out.println("[DEBUG TEST] ✓ Message deserialized successfully");
            System.out.println("[DEBUG TEST] Order ID: " + result.getOrderId());
            System.out.println("[DEBUG TEST] Patient: " + (result.getPatient() != null ? result.getPatient() : "null"));
            System.out.println("[DEBUG TEST] Observations count: " + result.getObservations().size());

            result.getObservations().forEach(obs -> {
                System.out.println("[DEBUG TEST]   - ID: " + obs.getId() +
                        ", LOINC: " + obs.getLoincCode() +
                        ", Result: " + obs.getResult() +
                        ", Type: " + obs.getResultType());
            });
        } else {
            System.out.println("[DEBUG TEST] ✗ Message deserialization returned null");
        }

        // Just log the result, don't assert - we're debugging
        assertNotNull(result, "Real message should deserialize successfully");
    }
}

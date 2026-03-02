package zm.gov.moh.hie.scp.deserializer;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v25.datatype.CE;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_OBSERVATION;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_ORDER_OBSERVATION;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_PATIENT_RESULT;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import zm.gov.moh.hie.scp.dto.Header;
import zm.gov.moh.hie.scp.dto.LabResult;
import zm.gov.moh.hie.scp.dto.Observation;
import zm.gov.moh.hie.scp.dto.Patient;
import zm.gov.moh.hie.scp.util.DateTimeUtil;
import zm.gov.moh.hie.scp.util.Hl7Parser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LabResultDeserializer implements DeserializationSchema<LabResult> {

    @Override
    public LabResult deserialize(byte[] bytes) {
        try {
            String oruR01String = new String(bytes);
            ORU_R01 oruMsg = Hl7Parser.toOru021Message(sanitize(oruR01String));

            if (oruMsg == null)
                return null;

            LabResult result = null;
            Header header = Hl7Parser.toHeader(oruMsg.getMSH()); // Set message header details
            Patient patient;
            String orderId = null;

            for (int i = 0; i < oruMsg.getPATIENT_RESULTReps(); i++) {
                ORU_R01_PATIENT_RESULT r = oruMsg.getPATIENT_RESULT(i);

                patient = Hl7Parser.toPatient(r.getPATIENT().getPID()); // Set patient details
                List<Observation> observations = new ArrayList<>();

                // OBR segments - Process all tests in the message
                for (int j = 0; j < r.getORDER_OBSERVATIONReps(); j++) {
                    ORU_R01_ORDER_OBSERVATION o = r.getORDER_OBSERVATION(j);

                    // Extract order ID only if not already set (first valid one wins)
                    if (orderId == null) {
                        try {
                            String extractedId = o.getORC().getPlacerOrderNumber().getEntityIdentifier().getValue();
                            if (extractedId != null && !extractedId.isBlank()) {
                                orderId = extractedId;
                            }
                        } catch (Exception e) {
                            // Order ID extraction failed, skip and continue
                        }
                    }

                    // Store OBR-4 value for Observation.id field
                    String testId = o.getOBR().getUniversalServiceIdentifier().getIdentifier().getValue();

                    LocalDateTime dateTime = LocalDateTime.parse(o.getOBR().getObservationDateTime().getTime().getValue() + "00", DateTimeUtil.DATETIME_DISA_FORMATTER);
                    LocalDateTime specimenReceivedTime = LocalDateTime.parse(o.getOBR().getSpecimenReceivedDateTime().getTime().getValue() + "00", DateTimeUtil.DATETIME_DISA_FORMATTER);

                    // Process only the last OBX segment in this OBR (which contains the actual result)
                    if (o.getOBSERVATIONAll().size() > 0) {
                        try {
                            ORU_R01_OBSERVATION observation = o.getOBSERVATIONAll().get(o.getOBSERVATIONAll().size() - 1);

                            String resultType = observation.getOBX().getValueType().getValue();
                            if (resultType == null || resultType.isBlank()) continue; // Skip empty value types

                            String orderResult;
                            String unit = null;

                            // Extract result based on value type
                            if (resultType.equals("CE")) {
                                orderResult = ((CE) observation.getOBX().getObservationValue(0).getData()).getIdentifier().getValue();
                            } else {
                                orderResult = observation.getOBX().getObservationValue(0).getData().toString();
                                if (observation.getOBX().getUnits() != null) {
                                    unit = observation.getOBX().getUnits().getIdentifier().getValue();
                                }
                            }

                            if (orderResult == null || orderResult.isBlank()) continue; // Skip if no result value

                            String resultStatus = observation.getOBX().getObservationResultStatus().getValue();

                            // Extract LOINC code from OBX-3 (Observation Identifier)
                            String loincCode = null;
                            try {
                                var obx3 = observation.getOBX().getObservationIdentifier();
                                if (obx3 != null) {
                                    // Get OBX-3 component 1 (the LOINC code)
                                    var comp1 = obx3.getComponent(0);
                                    String code = (comp1 != null) ? comp1.encode().trim() : null;

                                    // Get OBX-3 component 3 (the coding system - should be "LN" for LOINC)
                                    var comp3 = obx3.getComponent(2);
                                    String system = (comp3 != null) ? comp3.encode().trim() : null;

                                    // Use the code only if it's LOINC-coded and valid
                                    // System should be "LN" or contain "LN"
                                    if (code != null && !code.isBlank()) {
                                        if (system != null && (system.equals("LN") || system.contains("LN"))) {
                                            loincCode = code;
                                        } else if (system == null || system.isBlank()) {
                                            // If system is empty/null, assume it's LOINC if code looks valid (numeric-based)
                                            if (code.matches("^\\d+.*")) {
                                                loincCode = code;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Extraction failed, use default (null)
                            }

                            observations.add(
                                    new Observation(
                                            testId,
                                            dateTime.format(DateTimeUtil.TIMESTAMP_FORMATTER),
                                            specimenReceivedTime.format(DateTimeUtil.TIMESTAMP_FORMATTER),
                                            resultType,
                                            orderResult,
                                            unit,
                                            resultStatus,
                                            loincCode
                                    )
                            );
                        } catch (HL7Exception e) {
                            // Unable to process OBX
                        }
                    }
                }

                if (!observations.isEmpty())
                    result = new LabResult(header, orderId, patient, observations);
            }

            return result;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(LabResult nextElement) {
        return false;
    }

    // This method is super important for hapi to successfully serialize string to HL7 obj
    private String sanitize(String input) {
        input = input.replace("\n", "\r");
        input = input.replace("\r\r", "\r");

        input = input.replaceAll("(?m)^NTE\\|.*(?:\\r?\\n)?", "");

        input = input.replace("\r\r", "\r");

        return input;
    }

    @Override
    public TypeInformation<LabResult> getProducedType() {
        return TypeInformation.of(LabResult.class);
    }
}

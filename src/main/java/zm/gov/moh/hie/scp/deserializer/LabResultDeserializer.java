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

                // OBR segments
                for (int j = 0; j < r.getORDER_OBSERVATIONReps(); j++) {
                    ORU_R01_ORDER_OBSERVATION o = r.getORDER_OBSERVATION(j);
                    orderId = o.getORC().getPlacerOrderNumber().getEntityIdentifier().getValue(); // Get order ID
                    try {
                        // OBX segments
                        // We're only interest in the last OBX - the rest in nonsense.
                        ORU_R01_OBSERVATION observation = o.getOBSERVATIONAll().get(o.getOBSERVATIONAll().size() - 1);

                        String orderResult;
                        String unit = null;
                        String resultType;
                        String resultStatus = observation.getOBX().getObservationResultStatus().getValue();
                        String id = o.getOBR().getUniversalServiceIdentifier().getIdentifier().getValue();
                        LocalDateTime dateTime = LocalDateTime.parse(o.getOBR().getObservationDateTime().getTime().getValue() + "00", DateTimeUtil.DATETIME_DISA_FORMATTER);
                        LocalDateTime specimenReceivedTime = LocalDateTime.parse(o.getOBR().getSpecimenReceivedDateTime().getTime().getValue() + "00", DateTimeUtil.DATETIME_DISA_FORMATTER);

                        resultType = observation.getOBX().getValueType().getValue();
                        if (resultType.equals("CE")) {
                            orderResult = ((CE) observation.getOBX().getObservationValue(0).getData()).getIdentifier().getValue();
                        } else {
                            orderResult = observation.getOBX().getObservationValue(0).getData().toString();
                            unit = observation.getOBX().getUnits().getIdentifier().getValue();
                        }

                        observations.add(
                                new Observation(
                                        id,
                                        dateTime.format(DateTimeUtil.TIMESTAMP_FORMATTER),
                                        specimenReceivedTime.format(DateTimeUtil.TIMESTAMP_FORMATTER),
                                        resultType,
                                        orderResult,
                                        unit,
                                        resultStatus
                                )
                        );
                    } catch (HL7Exception ignored) {
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

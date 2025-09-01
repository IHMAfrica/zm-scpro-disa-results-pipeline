package zm.gov.moh.hie.scp.dto;

import java.io.Serializable;
import java.util.List;

public class LabResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private Header header;
    private Patient patient;
    private String OrderId;
    private List<Observation> observations;

    public LabResult() {}

    public LabResult(Header header, String orderId, Patient patient, List<Observation> observations) {
        this.header = header;
        this.OrderId = orderId;
        this.patient = patient;
        this.observations = observations;
    }

    public void setHeader(Header header) {
        this.header = header;
    }
    public void setPatient(Patient patient) {
        this.patient = patient;
    }
    public void setOrderId(String orderId) {
        this.OrderId = orderId;
    }
    public void setObservations(List<Observation> observations) {
        this.observations = observations;
    }
    public Header getHeader() {
        return header;
    }
    public Patient getPatient() {
        return patient;
    }
    public String getOrderId() {
        return OrderId;
    }
    public List<Observation> getObservations() {
        return observations;
    }

    @Override
    public String toString() {
        return "LabResult{" +
                "header=" + header +
                ", patient=" + patient +
                ", OrderId='" + OrderId + '\'' +
                ", observations=" + observations +
                '}';
    }
}

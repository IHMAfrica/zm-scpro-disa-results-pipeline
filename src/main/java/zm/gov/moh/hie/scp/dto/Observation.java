package zm.gov.moh.hie.scp.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Observation implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String timestamp;
    private String specimenReceivedTime;
    private String resultType;
    private String result;
    private String resultUnit;
    private String resultStatus;

    public Observation() {}

    public Observation(String id, String timestamp, String specimenReceivedTime, String resultType, String result, String resultUnit, String resultStatus) {
        this.id = id;
        this.timestamp = timestamp;
        this.specimenReceivedTime = specimenReceivedTime;
        this.resultType = resultType;
        this.result = result;
        this.resultUnit = resultUnit;
        this.resultStatus = resultStatus;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    private String getSpecimenReceivedTime() {
        return specimenReceivedTime;
    }
    public void setSpecimenReceivedTime(String specimenReceivedTime) {
        this.specimenReceivedTime = specimenReceivedTime;
    }
    public String getResultType() {
        return resultType;
    }
    public void setResultType(String resultType) {
        this.resultType = resultType;
    }
    public String getResult() {
        return result;
    }
    public void setResult(String result) {
        this.result = result;
    }
    public String getResultUnit() {
        return resultUnit;
    }
    public void setResultUnit(String resultUnit) {
        this.resultUnit = resultUnit;
    }
    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }
    public String getResultStatus() {
        return resultStatus;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Observation{");
        sb.append("id='").append(id).append('\'')
                .append(", timestamp=").append(timestamp)
                .append(", specimenReceivedTime=").append(specimenReceivedTime)
                .append(", resultType='").append(resultType).append('\'')
                .append(", result='").append(result).append('\'')
                .append(", resultUnit='").append(resultUnit).append('\'')
                .append(", resultStatus='").append(resultStatus).append('\'')
                .append('}');
        return sb.toString();
    }

}

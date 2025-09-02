package zm.gov.moh.hie.scp.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zm.gov.moh.hie.scp.dto.LabResult;
import zm.gov.moh.hie.scp.dto.Observation;
import zm.gov.moh.hie.scp.util.DateTimeUtil;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public class JdbcSink {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcSink.class);

    public static SinkFunction<LabResult> getLabResultSinkFunction(String jdbcUrl, String jdbcUser, String jdbcPassword) {
        // Configure a PostgreSQL JDBC sink for lab_results table
        final String upsertSql = "INSERT INTO crt.lab_result (hmis_code, order_id, result_date, result_time, result, message_ref_id) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (message_ref_id) DO UPDATE SET " +
                "result = EXCLUDED.result, " +
                "result_date = EXCLUDED.result_date, " +
                "result_time = EXCLUDED.result_time";

        JdbcStatementBuilder<LabResult> stmtBuilder = (PreparedStatement ps, LabResult element) -> {
            // 1. hmis_code (receiverId)
            String facilityId = element.getHeader() != null ? element.getHeader().getReceiverId() : null;
            if (facilityId == null) facilityId = "";
            ps.setString(1, facilityId);

            // 2. order_id (nullable)
            String orderId = element.getOrderId();
            if (orderId == null || orderId.isEmpty()) {
                ps.setNull(2, java.sql.Types.VARCHAR);
            } else {
                ps.setString(2, orderId);
            }

            // 3. result_date (parse header timestamp "yyyy-MM-dd")
            LocalDateTime timestamp = LocalDateTime.parse(element.getHeader().getTimestamp(), DateTimeUtil.TIMESTAMP_FORMATTER);
            Timestamp ts = Timestamp.valueOf(timestamp);
            ps.setDate(3, new java.sql.Date(ts.getTime()));

            // 4. result_time (parse header timestamp "HH:mm:ss")
            ps.setTime(4, new java.sql.Time(ts.getTime()));

            // 5. result (only set if a result has one result value)
            String result = (element.getObservations() != null && element.getObservations().size() == 1) ? element.getObservations().get(0).getResult() : null;
            if (result == null) {
                ps.setNull(5, java.sql.Types.VARCHAR);
            } else {
                ps.setString(5, result);
            }

            // 6. message_ref_id
            String messageRefId = element.getHeader() != null ? element.getHeader().getMessageId() : null;
            if (messageRefId == null || messageRefId.isEmpty()) {
                // If no id, skip by setting to a generated-like fallback (but ideally id should always exist)
                messageRefId = "unknown-" + System.currentTimeMillis();
            }
            ps.setString(6, messageRefId);
        };

        final var jdbcSink = org.apache.flink.connector.jdbc.JdbcSink.sink(
                upsertSql,
                stmtBuilder,
                JdbcExecutionOptions.builder()
                        .withBatchIntervalMs(2000)
                        .withBatchSize(50)
                        .withMaxRetries(5)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(jdbcUrl)
                        .withDriverName("org.postgresql.Driver")
                        .withUsername(jdbcUser)
                        .withPassword(jdbcPassword)
                        .build()
        );
        return jdbcSink;
    }

    public static SinkFunction<LabResult> getLabResultDataSinkFunction(String jdbcUrl, String jdbcUser, String jdbcPassword) {
        // Configure a PostgreSQL JDBC sink for lab_results table
        final String upsertSql = "INSERT INTO crt.lab_results_data (id, timestamp, facility_id, lab_id, order_id, observations) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "timestamp = EXCLUDED.timestamp, " +
                "facility_id = EXCLUDED.facility_id, " +
                "lab_id = EXCLUDED.lab_id, " +
                "order_id = EXCLUDED.order_id, " +
                "observations = EXCLUDED.observations";

        JdbcStatementBuilder<LabResult> stmtBuilder = (PreparedStatement ps, LabResult element) -> {
            // 1. id
            String id = element.getHeader() != null ? element.getHeader().getMessageId() : null;
            if (id == null || id.isEmpty()) {
                // If no id, skip by setting to a generated-like fallback (but ideally id should always exist)
                id = "unknown-" + System.currentTimeMillis();
            }
            ps.setString(1, id);

            // 2. timestamp (parse header timestamp "yyyy-MM-dd HH:mm:ss")
            Timestamp ts;
            try {
                String tsStr = element.getHeader() != null ? element.getHeader().getTimestamp() : null;
                if (tsStr != null && !tsStr.isEmpty()) {
                    LocalDateTime ldt = LocalDateTime.parse(tsStr, DateTimeUtil.TIMESTAMP_FORMATTER);
                    ts = Timestamp.valueOf(ldt);
                } else {
                    ts = new Timestamp(System.currentTimeMillis());
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse header timestamp, using current time. value={} error={}",
                        element.getHeader() != null ? element.getHeader().getTimestamp() : null, e.toString());
                ts = new Timestamp(System.currentTimeMillis());
            }
            ps.setTimestamp(2, ts);

            // 3. facility_id (receiverId)
            String facilityId = element.getHeader() != null ? element.getHeader().getReceiverId() : null;
            if (facilityId == null) facilityId = "";
            ps.setString(3, facilityId);

            // 4. lab_id (senderId)
            String labId = element.getHeader() != null ? element.getHeader().getSenderId() : null;
            if (labId == null) labId = "";
            ps.setString(4, labId);

            // 5. order_id (nullable)
            String orderId = element.getOrderId();
            if (orderId == null || orderId.isEmpty()) {
                ps.setNull(5, java.sql.Types.VARCHAR);
            } else {
                ps.setString(5, orderId);
            }

            // 6. observations jsonb[]
            String observationsArrayLiteral = buildPostgresJsonbArrayLiteral(element.getObservations());
            ps.setString(6, observationsArrayLiteral);
        };

        final var jdbcSink = org.apache.flink.connector.jdbc.JdbcSink.sink(
                upsertSql,
                stmtBuilder,
                JdbcExecutionOptions.builder()
                        .withBatchIntervalMs(2000)
                        .withBatchSize(50)
                        .withMaxRetries(5)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(jdbcUrl)
                        .withDriverName("org.postgresql.Driver")
                        .withUsername(jdbcUser)
                        .withPassword(jdbcPassword)
                        .build()
        );
        return jdbcSink;
    }

    // Build a Postgres array literal of JSON strings and cast to jsonb[] in SQL
    private static String buildPostgresJsonbArrayLiteral(List<Observation> observations) {
        try {
            if (observations == null || observations.isEmpty()) {
                return "[]"; // empty array
            }
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(observations);
        } catch (Exception ex) {
            return "[]";
        }
    }
}

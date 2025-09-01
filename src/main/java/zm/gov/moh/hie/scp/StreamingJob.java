package zm.gov.moh.hie.scp;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zm.gov.moh.hie.scp.deserializer.LabResultDeserializer;
import zm.gov.moh.hie.scp.dto.LabResult;
import zm.gov.moh.hie.scp.sink.JdbcSink;

public class StreamingJob {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    // Configuration constants
    private static final String KAFKA_BOOTSTRAP_SERVERS = "154.120.216.119:9093,102.23.120.153:9093,102.23.123.251:9093";
    private static final String KAFKA_TOPIC = "lab-results";
    private static final String KAFKA_GROUP_ID = "flink-es-consumer-6";
    // Kafka security configuration
    private static final String KAFKA_SECURITY_PROTOCOL = "SASL_PLAINTEXT";
    private static final String KAFKA_SASL_MECHANISM = "SCRAM-SHA-256";
    private static final String KAFKA_SASL_USERNAME = "admin";
    private static final String KAFKA_SASL_PASSWORD = "075F80FED7C6";
    // JDBC/PostgreSQL configuration
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/hie_manager";
    private static final String JDBC_USER = "postgres";
    private static final String JDBC_PASSWORD = "postgres";

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<LabResult> source = KafkaSource.<LabResult>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(KAFKA_TOPIC)
                .setGroupId(KAFKA_GROUP_ID)
                .setProperty("enable.auto.commit", "true")
                .setProperty("auto.commit.interval.ms", "2000")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new LabResultDeserializer())
                // Configure security settings for SASL_PLAINTEXT with SCRAM-SHA-256
                .setProperty("security.protocol", KAFKA_SECURITY_PROTOCOL)
                .setProperty("sasl.mechanism", KAFKA_SASL_MECHANISM)
                .setProperty("sasl.jaas.config",
                        "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                                "username=\"" + KAFKA_SASL_USERNAME + "\" " +
                                "password=\"" + KAFKA_SASL_PASSWORD + "\";")
                .build();

        // Create a DataStream from a Kafka source
        DataStream<LabResult> kafkaStream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "Kafka Source"
        ).startNewChain();

        // Filter out null values to prevent issues with the sink
        DataStream<LabResult> filteredStream = kafkaStream
                .filter(result -> {
                    if (result == null) {
                        LOG.warn("Filtered out null LabResult");
                        return false;
                    }
                    LOG.info("Processing LabResult with message ID: {}", result.getHeader().getMessageId());
                    return true;
                })
                .name("Filter Null Values").disableChaining();

        final var jdbcSink = JdbcSink.getLabResultSinkFunction(JDBC_URL, JDBC_USER, JDBC_PASSWORD);

        filteredStream.addSink(jdbcSink).name("Postgres JDBC -> Lab Meta Sink");

        final var jdbcDataSink = JdbcSink.getLabResultDataSinkFunction(JDBC_URL, JDBC_USER, JDBC_PASSWORD);

        filteredStream.addSink(jdbcDataSink).name("Postgres JDBC -> Lab Data Sink");

        // Execute the pipeline
        env.execute("Kafka to Postgres Pipeline");
    }

}
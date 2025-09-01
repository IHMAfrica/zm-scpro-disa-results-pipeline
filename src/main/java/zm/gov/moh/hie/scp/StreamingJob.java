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

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Load configuration from env and CLI args
        final Config cfg = Config.fromEnvAndArgs(args);

        KafkaSource<LabResult> source = KafkaSource.<LabResult>builder()
                .setBootstrapServers(cfg.kafkaBootstrapServers)
                .setTopics(cfg.kafkaTopic)
                .setGroupId(cfg.kafkaGroupId)
                .setProperty("enable.auto.commit", "true")
                .setProperty("auto.commit.interval.ms", "2000")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new LabResultDeserializer())
                // Configure security settings
                .setProperty("security.protocol", cfg.kafkaSecurityProtocol)
                .setProperty("sasl.mechanism", cfg.kafkaSaslMechanism)
                .setProperty("sasl.jaas.config",
                        "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                                "username=\"" + cfg.kafkaSaslUsername + "\" " +
                                "password=\"" + cfg.kafkaSaslPassword + "\";")
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

        final var jdbcSink = JdbcSink.getLabResultSinkFunction(cfg.jdbcUrl, cfg.jdbcUser, cfg.jdbcPassword);
        filteredStream.addSink(jdbcSink).name("Postgres JDBC -> Lab Meta Sink");

        final var jdbcDataSink = JdbcSink.getLabResultDataSinkFunction(cfg.jdbcUrl, cfg.jdbcUser, cfg.jdbcPassword);
        filteredStream.addSink(jdbcDataSink).name("Postgres JDBC -> Lab Data Sink");

        // Execute the pipeline
        env.execute("Kafka to Postgres Pipeline");
    }

}
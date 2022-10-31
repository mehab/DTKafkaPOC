package org.acme.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import io.quarkus.kafka.client.serialization.ObjectMapperSerializer;
import org.acme.analyzer.OssIndexAnalyzer;
import org.acme.model.Component;
import org.acme.model.VulnerabilityResultAggregate;
import org.acme.model.VulnerabilityResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AnalyzerTopology {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzerTopology.class);

    private final OssIndexAnalyzer ossIndexAnalyzer;

    @Inject
    public AnalyzerTopology(final OssIndexAnalyzer ossIndexAnalyzer) {
        this.ossIndexAnalyzer = ossIndexAnalyzer;
    }

    @Produces
    public Topology topology() {
        final var streamsBuilder = new StreamsBuilder();

        final var componentSerde = new ObjectMapperSerde<>(Component.class);
        final var componentsSerde = Serdes.serdeFrom(new ObjectMapperSerializer<>(),
                new ObjectMapperDeserializer<List<Component>>(new TypeReference<>() {
                }));
        final var vulnResultSerde = new ObjectMapperSerde<>(VulnerabilityResult.class);
        final var vulnResultsSerde = Serdes.serdeFrom(new ObjectMapperSerializer<>(),
                new ObjectMapperDeserializer<>(new TypeReference<List<VulnerabilityResult>>() {
                }));

        // Flat-Map incoming components from the API server, and re-key the stream from UUIDs to CPEs, PURLs, and SWID Tag IDs.
        // Every component from component-analysis can thus produce up to three new events.
        final KStream<String, Component> componentStream = streamsBuilder
                .stream("component-analysis", Consumed.with(Serdes.UUID(), new ObjectMapperSerde<>(Component.class)))
                .peek((k, v) -> LOGGER.info("Received component: {}", k))
                .flatMap((projectUuid, component) -> {
                    final var components = new ArrayList<KeyValue<String, Component>>();
                    if (component.getCpe() != null) {
                        // TODO: Canonicalize the CPE used as key, so that CPEs describing the same component end up in the same partition.
                        components.add(KeyValue.pair(component.getCpe(), component));
                    }
                    if (component.getPurl() != null) {
                        components.add(KeyValue.pair(component.getPurl().getCoordinates(), component));
                    }
                    if (component.getSwidTagId() != null) {
                        // NOTE: Barely any components have a SWID Tag ID yet, and no scanner supports it
                        components.add(KeyValue.pair(component.getSwidTagId(), component));
                    }
                    return components;
                }, Named.as("component-mapper"))
                .peek((identifier, component) -> LOGGER.info("Re-keyed component: {} -> {}", component.getUuid(), identifier));

        // Consume from the topic where analyzers write their results to.
        final KStream<String, VulnerabilityResult> analyzerResultStream = streamsBuilder
                .stream("component-analysis-vuln", Consumed.with(Serdes.String(), vulnResultSerde));

        // Re-key all vulnerability results from CPE/PURL/SWID back to component UUIDs.
        analyzerResultStream
                .peek((identifier, result) -> LOGGER.info("Re-keying result: {} -> {}", identifier, result.getComponent().getUuid()))
                .map((identifier, result) -> KeyValue.pair(result.getComponent().getUuid(), result), Named.as("vuln-result-mapper"))
                .to("component-vuln-analysis-result", Produced.with(Serdes.UUID(), vulnResultSerde));

        // Aggregate vulnerability results in a KTable.
        // This will map identifiers (CPE, PURL, SWID Tag ID) to identified vulnerabilities.
        final KTable<String, List<VulnerabilityResult>> cacheTable = analyzerResultStream
                .groupByKey()
                .aggregate(ArrayList::new,
                        (identity, result, aggregate) -> {
                            // Component details are not required at this level, because the vulnerabilities
                            // are only associated with identifiers.
                            // TODO: Maybe use a separate model class to make this more clear?
                            result.setComponent(null);

                            aggregate.add(result);
                            return aggregate;
                        }, Named.as("component-analysis-vuln-aggregate"),
                        Materialized.<String, List<VulnerabilityResult>, KeyValueStore<Bytes, byte[]>>as("component-analysis-vuln-aggregate")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(vulnResultsSerde));

        // Left-Join the stream of components with the cacheTable constructed above.
        final KStream<String, VulnerabilityResultAggregate> componentCacheJoinStream = componentStream
                .leftJoin(cacheTable, (component, vulnerabilityResults) -> {
                            final var aggregate = new VulnerabilityResultAggregate();
                            aggregate.setComponent(component);
                            if (vulnerabilityResults != null) {
                                aggregate.setVulnerabilityResults(vulnerabilityResults);
                            }
                            return aggregate;
                        },
                        Joined.with(Serdes.String(), componentSerde, vulnResultsSerde).withName("component-cache-join"))
                .peek((k, v) -> LOGGER.info("Joined: {} -> {}", k, v));

        componentCacheJoinStream
                .split(Named.as("vuln-cache"))
                .branch(
                        (identifier, vulnResultAggregate) -> vulnResultAggregate.getVulnerabilityResults() != null
                                && vulnResultAggregate.getVulnerabilityResults().size() > 0,
                        Branched.<String, VulnerabilityResultAggregate>withConsumer(vulnResultAggregateStream -> vulnResultAggregateStream
                                .peek((identifier, vulnResultAggregate) -> LOGGER.info("Cache hit: {}", identifier))
                                .flatMapValues((identifier, vulnResultAggregate) -> vulnResultAggregate.getVulnerabilityResults().stream()
                                        .peek(vulnResult -> vulnResult.setComponent(vulnResultAggregate.getComponent()))
                                        .toList())
                                .map((identifier, vulnResult) -> KeyValue.pair(vulnResult.getComponent().getUuid(), vulnResult))
                                .to("component-vuln-analysis-result", Produced.with(Serdes.UUID(), vulnResultSerde))
                        ).withName("hit")
                )
                .defaultBranch(
                        Branched.<String, VulnerabilityResultAggregate>withConsumer(missed -> {
                            // Branch off component events to different topics, based on their identifiers.
                            final Map<String, KStream<String, Component>> branches = missed
                                    .peek((identifier, vulnResultAggregate) -> LOGGER.info("Cache miss: {}", identifier))
                                    .mapValues(VulnerabilityResultAggregate::getComponent)
                                    .split(Named.as("component-analysis-"))
                                    .branch((identifier, component) -> isCpe(identifier), Branched.as("cpe"))
                                    .branch((identifier, component) -> isPurl(identifier), Branched.as("purl"))
                                    .branch((identifier, component) -> isSwidTagId(identifier), Branched.as("swid"))
                                    .defaultBranch(Branched.as("default"));
                            branches.get("component-analysis-cpe").to("component-analysis-cpe", Produced.with(Serdes.String(), componentSerde));
                            branches.get("component-analysis-purl").to("component-analysis-purl", Produced.with(Serdes.String(), componentSerde));
                            branches.get("component-analysis-swid").to("component-analysis-swid", Produced.with(Serdes.String(), componentSerde));
                            branches.get("component-analysis-default").foreach((identifier, component) -> LOGGER.warn("Unmatched branch: {}", identifier));
                        }).withName("miss")
                );

        // Perform a time window based aggregation of components with PURLs.
        // TODO: Repeat this for CPE and SWID Tag ID
        final KStream<Windowed<Integer>, List<Component>> purlAggregateStream = streamsBuilder
                .stream("component-analysis-purl", Consumed.with(Serdes.String(), componentSerde))
                .groupBy((purl, component) -> Utils.toPositive(Utils.murmur2(purl.getBytes(StandardCharsets.UTF_8))) % 3, Grouped.with(Serdes.Integer(), componentSerde))
                .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .aggregate(ArrayList::new,
                        (partition, component, aggregate) -> {
                            aggregate.add(component);
                            return aggregate;
                        },
                        Materialized.<Integer, List<Component>, WindowStore<Bytes, byte[]>>as("component-analysis-purl-aggregate")
                                .withKeySerde(Serdes.Integer())
                                .withValueSerde(componentsSerde))
                .toStream();

        // Perform the actual analysis based on the PURL aggregates.
        purlAggregateStream
                .flatMap((window, components) -> analyzeOssIndex(components), Named.as("ossindex-analyzer"))
                .to("component-analysis-vuln", Produced.with(Serdes.String(), vulnResultSerde));
        purlAggregateStream
                .flatMap((window, components) -> analyzeSnyk(components), Named.as("snyk-analyzer"))
                .to("component-analysis-vuln", Produced.with(Serdes.String(), vulnResultSerde));

        return streamsBuilder.build();
    }

    private boolean isPurl(final String purl) {
        try {
            new PackageURL(purl);
            return true;
        } catch (MalformedPackageURLException e) {
            return false;
        }
    }

    private boolean isCpe(final String cpe) {
        return StringUtils.startsWith(cpe, "cpe:");
    }

    private boolean isSwidTagId(final String swidTagId) {
        return false;
    }

    private List<KeyValue<String, VulnerabilityResult>> analyzeOssIndex(final List<Component> components) {
        LOGGER.info("Performing OSS Index analysis for {} components: {}", components.size(), components);
        return ossIndexAnalyzer.analyze(components).stream()
                .map(vulnResult -> KeyValue.pair(vulnResult.getComponent().getPurl().getCoordinates(), vulnResult))
                .toList();
    }

    private List<KeyValue<String, VulnerabilityResult>> analyzeSnyk(final List<Component> components) {
        LOGGER.info("Performing Snyk analysis for {} components: {}", components.size(), components);
        return Collections.emptyList();
    }

}
package de.otto.synapse.edison.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import de.otto.edison.navigation.NavBar;
import de.otto.synapse.channel.ShardPosition;
import de.otto.synapse.endpoint.EndpointType;
import de.otto.synapse.message.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.ManagementServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;

import static de.otto.edison.navigation.NavBarItem.navBarItem;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@Controller
@ConditionalOnBean(MessageTrace.class)
public class MessageTraceController {

    private final MessageTrace messageTrace;
    private final ObjectMapper objectMapper;

    @Autowired
    MessageTraceController(final MessageTrace messageTrace,
                           final NavBar rightNavBar,
                           final ManagementServerProperties managementServerProperties,
                           final ObjectMapper objectMapper) {
        this.messageTrace = messageTrace;
        this.objectMapper = objectMapper;
        rightNavBar.register(
                navBarItem(10, "Message Trace", format("%s/messagetrace", managementServerProperties.getContextPath()))
        );
        messageTrace.getSenderChannels().forEach(channelName -> rightNavBar.register(
                navBarItem(11, "Sender: " + channelName, format("%s/messagetrace/sender/%s", managementServerProperties.getContextPath(), channelName))
        ));
        messageTrace.getReceiverChannels().forEach(channelName -> rightNavBar.register(
                navBarItem(12, "Receiver: " + channelName, format("%s/messagetrace/receiver/%s", managementServerProperties.getContextPath(), channelName))
        ));
    }

    @GetMapping(
            path = "${management.context-path}/messagetrace/{endpointType}/{channelName}",
            produces = "text/html"
    )
    public ModelAndView getMessageTrace(final @PathVariable String endpointType,
                                        final @PathVariable String channelName) {
        return new ModelAndView(
                "single-channel-trace",
                ImmutableMap.of(
                        "title", endpointType.equals("receiver") ? "Receiver: " : "Sender: " + channelName,
                        "messages",
                        messageTrace
                                .stream(channelName, EndpointType.valueOf(endpointType.toUpperCase()))
                                .map(traceEntry -> ImmutableMap.of(
                                        "sequenceNumber", traceEntry.getSequenceNumber(),
                                        "key", traceEntry.getMessage().getKey(),
                                        "header", prettyPrint(traceEntry.getMessage().getHeader()),
                                        "payload", prettyPrint(traceEntry.getMessage().getPayload())
                                ))
                                .collect(toList())
                )
        );
    }

    @GetMapping(
            path = "${management.context-path}/messagetrace",
            produces = "text/html"
    )
    public ModelAndView getMessageTrace() {
        return new ModelAndView(
                "multi-channel-trace",
                ImmutableMap.of(
                        "title",
                        "Message Trace",
                        "messages",
                        messageTrace
                                .stream()
                                .map(traceEntry -> ImmutableMap.builder()
                                        .put("sequenceNumber", traceEntry.getSequenceNumber())
                                        .put("ts", LocalDateTime.ofInstant(traceEntry.getTimestamp(), ZoneId.systemDefault()).toString())
                                        .put("channelName", traceEntry.getChannelName())
                                        .put("endpointType", traceEntry.getEndpointType().name())
                                        .put("key", traceEntry.getMessage().getKey())
                                        .put("header", prettyPrint(traceEntry.getMessage().getHeader()))
                                        .put("payload", prettyPrint(traceEntry.getMessage().getPayload()))
                                        .build()
                                )
                                .collect(toList())
                )
        );
    }

    private String prettyPrint(final String json) {
        try {
            Object jsonObject = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String prettyPrint(final Header header) {
        try {

            final LinkedHashMap<Object, Object> map = Maps.newLinkedHashMap();
            map.put("shardPosition", header.getShardPosition().map(ShardPosition::toString).orElse(""));
            map.put("arrivalTimestamp", header.getArrivalTimestamp().toString());
            map.put("attributes", header.getAttributes());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}

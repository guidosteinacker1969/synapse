package de.otto.edison.eventsourcing.inmemory;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.edison.eventsourcing.consumer.AbstractEventSource;
import de.otto.edison.eventsourcing.consumer.StreamPosition;
import de.otto.edison.eventsourcing.event.Event;
import de.otto.edison.eventsourcing.event.EventBody;

import java.time.Instant;
import java.util.function.Predicate;

public class InMemoryEventSource extends AbstractEventSource {


    private final InMemoryStream inMemoryStream;

    public InMemoryEventSource(final String name,
                               final InMemoryStream inMemoryStream,
                               final ObjectMapper objectMapper) {
        super(name, objectMapper);
        this.inMemoryStream = inMemoryStream;
    }

    @Override
    public String getStreamName() {
        return getName();
    }

    @Override
    public StreamPosition consumeAll(StreamPosition startFrom, Predicate<Event<?>> stopCondition) {
        boolean shouldStop;
        do {
            EventBody<String> eventBody = inMemoryStream.receive();

            if (eventBody == null) {
                return null;
            }

            Event<String> event = Event.event(eventBody, "0", Instant.now());

            registeredConsumers().encodeAndSend(event);
            shouldStop = stopCondition.test(event);
        } while (!shouldStop);
        return null;
    }
}

package de.otto.synapse.eventsource;

import de.otto.synapse.channel.ChannelPosition;
import de.otto.synapse.consumer.DispatchingMessageConsumer;
import de.otto.synapse.consumer.MessageConsumer;
import de.otto.synapse.message.Message;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An event source of {@link Message events}.
 * <p>
 *     Event sources can be consumed by {@link Consumer consumers}.
 * </p>
 *
 */
public interface EventSource {

    /**
     * An event source's name is used to connect event sources to their consumers.
     * @return the unique name
     */
    String getName();

    /**
     * Registers a new EventConsumer at the EventSource.
     *
     * {@link MessageConsumer consumers} have to be thread safe as it may be called from multiple threads
     * (e.g. for kinesis streams there is one thread per shard)
     *
     * @param messageConsumer registered EventConsumer
     */
    void register(MessageConsumer<?> messageConsumer);

    /**
     * Returns registered EventConsumers.
     *
     * @return EventConsumers
     */
    DispatchingMessageConsumer dispatchingMessageConsumer();

    /**
     * Returns the name of the EventSource.
     * <p>
     *     For streaming event-sources, this is the name of the event stream.
     * </p>
     *
     * @return name
     */
    String getStreamName();

    /**
     * Consumes all events from the EventSource, until the (current) end of the stream is reached.
     * <p>
     *     The registered {@link MessageConsumer consumers} will be called zero or more times, depending on
     *     the number of events retrieved from the EventSource.
     * </p>
     *
     * @return the new read position
     */
    default ChannelPosition consumeAll() {
        return consumeAll(ChannelPosition.fromHorizon(), event -> false);
    }

    /**
     * Consumes all events from the EventSource, beginning with {@link ChannelPosition startFrom}, until
     * the (current) end of the stream is reached.
     * <p>
     *     The registered {@link MessageConsumer consumers} will be called zero or more times, depending on
     *     the number of events retrieved from the EventSource.
     * </p>
     *
     * @param startFrom the read position returned from earlier executions
     * @return the new read position
     */
    default ChannelPosition consumeAll(ChannelPosition startFrom) {
        return consumeAll(startFrom, event -> false);
    }

    /**
     * Consumes all events from the EventSource until the {@link Predicate stopCondition} is met.
     * <p>
     *     The registered {@link MessageConsumer consumers} will be called zero or more times, depending on
     *     the number of events retrieved from the EventSource.
     * </p>
     *
     * @param stopCondition the predicate used as a stop condition
     * @return the new read position
     */
    default ChannelPosition consumeAll(Predicate<Message<?>> stopCondition) {
        return consumeAll(ChannelPosition.fromHorizon(), stopCondition);
    }

    /**
     * Consumes all events from the EventSource, beginning with {@link ChannelPosition startFrom}, until
     * the {@link Predicate stopCondition} is met.
     * <p>
     *     The registered {@link MessageConsumer consumers} will be called zero or more times, depending on
     *     the number of events retrieved from the EventSource.
     * </p>
     *
     * @param startFrom the read position returned from earlier executions
     * @param stopCondition the predicate used as a stop condition
     * @return the new read position
     */
    ChannelPosition consumeAll(ChannelPosition startFrom,
                               Predicate<Message<?>> stopCondition);

    void stop();

    boolean isStopping();
}

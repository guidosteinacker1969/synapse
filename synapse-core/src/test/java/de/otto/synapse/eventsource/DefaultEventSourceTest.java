package de.otto.synapse.eventsource;

import de.otto.synapse.channel.ChannelPosition;
import de.otto.synapse.consumer.MessageDispatcher;
import de.otto.synapse.endpoint.InterceptorChain;
import de.otto.synapse.endpoint.receiver.MessageLogReceiverEndpoint;
import de.otto.synapse.message.Message;
import de.otto.synapse.messagestore.MessageStore;
import org.junit.Test;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static de.otto.synapse.channel.ChannelPosition.channelPosition;
import static de.otto.synapse.channel.ChannelPosition.fromHorizon;
import static de.otto.synapse.channel.ShardPosition.fromPosition;
import static de.otto.synapse.message.Header.responseHeader;
import static de.otto.synapse.message.Message.message;
import static de.otto.synapse.messagestore.MessageStores.emptyMessageStore;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DefaultEventSourceTest {

    @Test
    public void shouldReadMessagesFromMessageStore() throws ExecutionException, InterruptedException {
        // given
        final MessageStore messageStore = mockMessageStore(fromHorizon());
        final MessageLogReceiverEndpoint messageLog = mockMessageLogReceiverEndpoint();
        final DefaultEventSource eventSource = new DefaultEventSource(messageStore, messageLog);

        // when
        eventSource.consume().get();

        // then
        verify(messageStore).stream();
    }

    @Test
    public void shouldReadMessagesFromMessageLog() throws ExecutionException, InterruptedException {
        // given
        final MessageStore messageStore = mockMessageStore(fromHorizon());
        final MessageLogReceiverEndpoint messageLog = mockMessageLogReceiverEndpoint();
        final DefaultEventSource eventSource = new DefaultEventSource(messageStore, messageLog);

        // when
        eventSource.consume().get();

        // then
        verify(messageLog).consumeUntil(fromHorizon(), Instant.MAX);
    }

    @Test
    public void shouldInterceptMessagesFromMessageStore() throws ExecutionException, InterruptedException {
        // given
        final Instant arrivalTimestamp = Instant.now();
        // and some message store having a single message
        final MessageStore messageStore = mock(MessageStore.class);
        when(messageStore.stream()).thenReturn(Stream.of(message("1", responseHeader(null, arrivalTimestamp), null)));
        when(messageStore.getLatestChannelPosition()).thenReturn(fromHorizon());
        // and some MessageLogReceiverEndpoint with an InterceptorChain:
        final InterceptorChain interceptorChain = mock(InterceptorChain.class);
        final MessageLogReceiverEndpoint messageLog = mock(MessageLogReceiverEndpoint.class);
        when(messageLog.getInterceptorChain()).thenReturn(interceptorChain);
        when(messageLog.consumeUntil(any(ChannelPosition.class), any(Instant.class))).thenReturn(completedFuture(fromHorizon()));

        // and our famous DefaultEventSource:
        final DefaultEventSource eventSource = new DefaultEventSource(messageStore, messageLog);

        // when
        eventSource.consume().get();

        // then
        verify(interceptorChain).intercept(
                message("1", responseHeader(null, arrivalTimestamp), null)
        );
    }

    @Test
    public void shouldDropNullMessagesInterceptedFromMessageStore() throws ExecutionException, InterruptedException {
        // given
        final Instant arrivalTimestamp = Instant.now();
        // and some message store having a single message
        final MessageStore messageStore = mock(MessageStore.class);
        when(messageStore.stream()).thenReturn(Stream.of(message("1", responseHeader(null, arrivalTimestamp), null)));
        when(messageStore.getLatestChannelPosition()).thenReturn(fromHorizon());
        // and some InterceptorChain that is dropping messages:
        final InterceptorChain interceptorChain = new InterceptorChain();
        interceptorChain.register((m)->null);
        // and some MessageLogReceiverEndpoint with our InterceptorChain:
        final MessageLogReceiverEndpoint messageLog = mock(MessageLogReceiverEndpoint.class);
        when(messageLog.consumeUntil(any(ChannelPosition.class), any(Instant.class))).thenReturn(completedFuture(fromHorizon()));
        when(messageLog.getInterceptorChain()).thenReturn(interceptorChain);
        final MessageDispatcher messageDispatcher = mock(MessageDispatcher.class);
        when(messageLog.getMessageDispatcher()).thenReturn(messageDispatcher);
        // and our famous DefaultEventSource:
        final DefaultEventSource eventSource = new DefaultEventSource(messageStore, messageLog);

        // when
        eventSource.consume().get();

        // then
        verify(messageDispatcher, never()).accept(any(Message.class));
    }

    @Test
    public void shouldDispatchMessagesFromMessageStore() throws ExecutionException, InterruptedException {
        // given
        final Instant arrivalTimestamp = Instant.now();
        // and some message store having a single message
        final MessageStore messageStore = mock(MessageStore.class);
        when(messageStore.stream()).thenReturn(Stream.of(message("1", responseHeader(null, arrivalTimestamp), null)));
        when(messageStore.getLatestChannelPosition()).thenReturn(fromHorizon());
        // and some MessageLogReceiverEndpoint with our InterceptorChain:
        final MessageLogReceiverEndpoint messageLog = mock(MessageLogReceiverEndpoint.class);
        when(messageLog.getInterceptorChain()).thenReturn(new InterceptorChain());
        when(messageLog.consumeUntil(any(ChannelPosition.class), any(Instant.class))).thenReturn(completedFuture(fromHorizon()));
        final MessageDispatcher messageDispatcher = mock(MessageDispatcher.class);
        when(messageLog.getMessageDispatcher()).thenReturn(messageDispatcher);
        // and our famous DefaultEventSource:
        final DefaultEventSource eventSource = new DefaultEventSource(messageStore, messageLog);

        // when
        eventSource.consume().get();

        // then
        verify(messageDispatcher).accept(message("1", responseHeader(null, arrivalTimestamp), null));
    }

    @Test
    public void shouldContinueWithChannelPositionFromMessageStore() throws ExecutionException, InterruptedException {
        // given
        final ChannelPosition expectedChannelPosition = channelPosition(fromPosition("bar", "42"));
        // and some message store having a single message
        final MessageStore messageStore = mockMessageStore(expectedChannelPosition);
        // and some MessageLogReceiverEndpoint with our InterceptorChain:
        final MessageLogReceiverEndpoint messageLog = mockMessageLogReceiverEndpoint();
        // and our famous DefaultEventSource:
        final DefaultEventSource eventSource = new DefaultEventSource(messageStore, messageLog);

        // when
        eventSource.consume().get();

        // then
        verify(messageLog).consumeUntil(expectedChannelPosition, Instant.MAX);
    }

    @Test
    public void shouldCloseMessageStore() throws Exception {
        // given
        final MessageStore messageStore = mockMessageStore(fromHorizon());
        final MessageLogReceiverEndpoint messageLog = mockMessageLogReceiverEndpoint();
        final DefaultEventSource eventSource = new DefaultEventSource(messageStore, messageLog);

        // when
        eventSource.consume().get();

        // then
        verify(messageStore).close();
    }

    @Test
    public void shouldStopMessageLogReceiverEndpoint() throws Exception {
        // given
        final MessageLogReceiverEndpoint messageLog = mock(MessageLogReceiverEndpoint.class);
        final DefaultEventSource eventSource = new DefaultEventSource(emptyMessageStore(), messageLog);

        // when
        eventSource.stop();

        // then
        verify(messageLog).stop();
        assertThat(eventSource.isStopping(), is(true));
    }

    private MessageLogReceiverEndpoint mockMessageLogReceiverEndpoint() {
        final MessageLogReceiverEndpoint messageLog = mock(MessageLogReceiverEndpoint.class);
        when(messageLog.consumeUntil(any(ChannelPosition.class), any(Instant.class))).thenReturn(completedFuture(fromHorizon()));
        return messageLog;
    }

    private MessageStore mockMessageStore(final ChannelPosition expectedChannelPosition) {
        final MessageStore messageStore = mock(MessageStore.class);
        when(messageStore.getLatestChannelPosition()).thenReturn(expectedChannelPosition);
        when(messageStore.stream()).thenReturn(Stream.empty());
        return messageStore;
    }
}
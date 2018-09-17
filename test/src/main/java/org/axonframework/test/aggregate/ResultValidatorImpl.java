/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResponseMessage;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.test.FixtureExecutionException;
import org.axonframework.test.deadline.DeadlineManagerValidator;
import org.axonframework.test.deadline.StubDeadlineManager;
import org.axonframework.test.matchers.EqualFieldsMatcher;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.matchers.MapEntryMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.axonframework.test.matchers.Matchers.messageWithPayload;
import static org.hamcrest.CoreMatchers.*;

/**
 * Implementation of the ResultValidator. It also acts as a CommandCallback, and registers the actual result.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class ResultValidatorImpl<T> implements ResultValidator<T>, CommandCallback<Object, Object> {

    private final List<EventMessage<?>> publishedEvents;
    private final Reporter reporter = new Reporter();
    private final FieldFilter fieldFilter;
    private final Supplier<Aggregate<T>> state;
    private final DeadlineManagerValidator deadlineManagerValidator;
    private CommandResponseMessage<?> actualReturnValue;
    private Throwable actualException;

    /**
     * Initialize the ResultValidatorImpl with the given {@code storedEvents} and {@code publishedEvents}.
     *
     * @param publishedEvents The events that were published during command execution
     * @param fieldFilter     The filter describing which fields to include in the comparison
     */
    public ResultValidatorImpl(List<EventMessage<?>> publishedEvents,
                               FieldFilter fieldFilter,
                               Supplier<Aggregate<T>> aggregateState,
                               StubDeadlineManager stubDeadlineManager) {
        this.publishedEvents = publishedEvents;
        this.fieldFilter = fieldFilter;
        this.state = aggregateState;
        this.deadlineManagerValidator = new DeadlineManagerValidator(stubDeadlineManager, fieldFilter);
    }

    @Override
    public ResultValidator<T> expectEvents(Object... expectedEvents) {
        if (expectedEvents.length != publishedEvents.size()) {
            reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
        }

        Iterator<EventMessage<?>> iterator = publishedEvents.iterator();
        for (Object expectedEvent : expectedEvents) {
            EventMessage actualEvent = iterator.next();
            if (!verifyEventEquality(expectedEvent, actualEvent.getPayload())) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @Override
    public ResultValidator<T> expectEvents(EventMessage... expectedEvents) {
        this.expectEvents(Stream.of(expectedEvents).map(Message::getPayload).toArray());

        Iterator<EventMessage<?>> iterator = publishedEvents.iterator();
        for (EventMessage expectedEvent : expectedEvents) {
            EventMessage actualEvent = iterator.next();
            if (!verifyMetaDataEquality(expectedEvent.getPayloadType(),
                                        expectedEvent.getMetaData(),
                                        actualEvent.getMetaData())) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @Override
    public ResultValidator<T> expectEventsMatching(Matcher<? extends List<? super EventMessage<?>>> matcher) {
        if (!matcher.matches(publishedEvents)) {
            reporter.reportWrongEvent(publishedEvents, descriptionOf(matcher), actualException);
        }
        return this;
    }

    private StringDescription descriptionOf(Matcher<?> matcher) {
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        return description;
    }

    @Override
    public ResultValidator<T> expectSuccessfulHandlerExecution() {
        return expectReturnValueMatching(anything());
    }

    @Override
    public ResultValidator<T> expectState(Consumer<T> aggregateStateValidator) {
        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        try {
            state.get().execute(aggregateStateValidator);
        } finally {
            uow.rollback();
        }
        return this;
    }

    @Override
    public ResultValidator<T> expectScheduledDeadlineMatching(Duration duration,
                                                              Matcher<? super DeadlineMessage<?>> matcher) {
        deadlineManagerValidator.assertScheduledDeadlineMatching(duration, matcher);
        return this;
    }

    @Override
    public ResultValidator<T> expectScheduledDeadline(Duration duration, Object deadline) {
        return expectScheduledDeadlineMatching(duration, messageWithPayload(Matchers.equalTo(deadline, fieldFilter)));
    }

    @Override
    public ResultValidator<T> expectScheduledDeadlineOfType(Duration duration, Class<?> deadlineType) {
        return expectScheduledDeadlineMatching(duration, messageWithPayload(any(deadlineType)));
    }

    @Override
    public ResultValidator<T> expectScheduledDeadlineMatching(Instant scheduledTime,
                                                              Matcher<? super DeadlineMessage<?>> matcher) {
        deadlineManagerValidator.assertScheduledDeadlineMatching(scheduledTime, matcher);
        return this;
    }

    @Override
    public ResultValidator<T> expectScheduledDeadline(Instant scheduledTime, Object deadline) {
        return expectScheduledDeadlineMatching(scheduledTime,
                                               messageWithPayload(Matchers.equalTo(deadline, fieldFilter)));
    }

    @Override
    public ResultValidator<T> expectScheduledDeadlineOfType(Instant scheduledTime, Class<?> deadlineType) {
        return expectScheduledDeadlineMatching(scheduledTime, messageWithPayload(any(deadlineType)));
    }

    @Override
    public ResultValidator<T> expectNoScheduledDeadlines() {
        deadlineManagerValidator.assertNoScheduledDeadlines();
        return this;
    }

    @Override
    public ResultValidator<T> expectDeadlinesMetMatching(Matcher<? extends List<? super DeadlineMessage<?>>> matcher) {
        deadlineManagerValidator.assertDeadlinesMetMatching(matcher);
        return this;
    }

    @Override
    public ResultValidator<T> expectDeadlinesMet(Object... expected) {
        deadlineManagerValidator.assertDeadlinesMet(expected);
        return this;
    }

    @Override
    public ResultValidator<T> expectReturnValue(Object expectedReturnValue) {
        if (expectedReturnValue == null) {
            return expectReturnValueMatching(nullValue());
        }
        return expectReturnValueMatching(equalTo(expectedReturnValue));
    }

    @Override
    public ResultValidator<T> expectReturnValueMatching(Matcher<?> matcher) {
        if (matcher == null) {
            return expectReturnValueMatching(nullValue());
        }
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, description);
        } else if (!matcher.matches(actualReturnValue.getPayload())) {
            reporter.reportWrongResult(actualReturnValue.getPayload(), description);
        }
        return this;
    }

    @Override
    public ResultValidator<T> expectExceptionMessage(Matcher<?> exceptionMessageMatcher) {
        StringDescription emptyMatcherDescription = new StringDescription(
                new StringBuilder("Given exception message matcher is null!"));
        if (exceptionMessageMatcher == null) {
            reporter.reportWrongExceptionMessage(actualException, emptyMatcherDescription);
            return this;
        }
        StringDescription description = new StringDescription();
        exceptionMessageMatcher.describeTo(description);
        if (actualException != null && !exceptionMessageMatcher.matches(actualException.getMessage())) {
            reporter.reportWrongExceptionMessage(actualException, description);
        }
        return this;
    }

    @Override
    public ResultValidator<T> expectExceptionMessage(String exceptionMessage) {
        return expectExceptionMessage(equalTo(exceptionMessage));
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public ResultValidator<T> expectException(Class<? extends Throwable> expectedException) {
        return expectException(instanceOf(expectedException));
    }

    @Override
    public ResultValidator<T> expectException(Matcher<?> matcher) {
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualReturnValue.getPayload(), description);
        }
        if (!matcher.matches(actualException)) {
            reporter.reportWrongException(actualException, description);
        }
        return this;
    }

    @Override
    public void onSuccess(CommandMessage<?> commandMessage, CommandResponseMessage<?> commandResponseMessage) {
        actualReturnValue = commandResponseMessage;
    }

    @Override
    public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
        actualException = cause;
    }

    /**
     * Makes sure the execution phase has finishes without any Errors ir FixtureExecutionExceptions. If an error was
     * recorded, it will be thrown immediately. This allow one to distinguish between failed tests, and tests in error.
     */
    public void assertValidRecording() {
        if (actualException instanceof Error) {
            throw (Error) actualException;
        } else if (actualException instanceof FixtureExecutionException) {
            throw (FixtureExecutionException) actualException;
        }
    }

    private boolean verifyEventEquality(Object expectedEvent, Object actualEvent) {
        if (!expectedEvent.getClass().equals(actualEvent.getClass())) {
            return false;
        }
        EqualFieldsMatcher<Object> matcher = new EqualFieldsMatcher<>(expectedEvent, fieldFilter);
        if (!matcher.matches(actualEvent)) {
            reporter.reportDifferentEventContents(expectedEvent.getClass(),
                                                  matcher.getFailedField(),
                                                  matcher.getFailedFieldActualValue(),
                                                  matcher.getFailedFieldExpectedValue());
        }
        return true;
    }

    private boolean verifyMetaDataEquality(Class<?> eventType, Map<String, Object> expectedMetaData,
                                           Map<String, Object> actualMetaData) {
        MapEntryMatcher matcher = new MapEntryMatcher(expectedMetaData);
        if (!matcher.matches(actualMetaData)) {
            reporter.reportDifferentMetaData(eventType, matcher.getMissingEntries(), matcher.getAdditionalEntries());
        }
        return true;
    }
}

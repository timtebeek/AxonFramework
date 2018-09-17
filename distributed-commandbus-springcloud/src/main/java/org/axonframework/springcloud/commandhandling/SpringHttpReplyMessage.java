/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.springcloud.commandhandling;

import org.axonframework.commandhandling.CommandResponseMessage;
import org.axonframework.commandhandling.distributed.ReplyMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;

/**
 * Spring Http Message representing a reply to a dispatched command.
 */
public class SpringHttpReplyMessage<R> extends ReplyMessage implements Serializable {

    /**
     * Initializes a SpringHttpReplyMessage containing a reply to the command with given {commandIdentifier} and given
     * {@code commandResponseMessage}. The parameter {@code success} determines whether the was executed successfully or
     * not.
     *
     * @param commandIdentifier      The identifier of the command to which the message is a reply
     * @param success                Whether or not the command executed successfully or not
     * @param commandResponseMessage The return value of command process
     *                               the given {@code commandResponseMessage} is ignored.
     * @param serializer             The serializer to serialize the message contents with
     */
    public SpringHttpReplyMessage(String commandIdentifier, boolean success, CommandResponseMessage<R> commandResponseMessage,
                                  Serializer serializer) {
        super(commandIdentifier, success, commandResponseMessage, serializer);
    }

    @SuppressWarnings("unused")
    private SpringHttpReplyMessage() {
        // Used for de-/serialization
    }

    @Override
    @SuppressWarnings("unchecked")
    public CommandResponseMessage<R> getCommandResponseMessage(Serializer serializer) {
        return (CommandResponseMessage<R>) super.getCommandResponseMessage(serializer);
    }

}

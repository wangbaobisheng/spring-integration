/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.groovy.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.groovy.GroovyScriptExecutingMessageProcessor;
import org.springframework.integration.handler.MessageProcessor;
import org.springframework.integration.router.MethodInvokingRouter;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Mark Fisher
 * @author Artem Bilan
 *
 * @since 2.0
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class GroovyRouterTests {

	@Autowired
	private MessageChannel referencedScriptInput;

	@Autowired
	private MessageChannel inlineScriptInput;

	@Autowired
	private PollableChannel longStrings;

	@Autowired
	private PollableChannel shortStrings;

	@Autowired
	@Qualifier("groovyRouter.handler")
	private MessageHandler groovyRouterMessageHandler;

	@Test
	public void referencedScript() { // long is > 3
		Message<?> message1 = new GenericMessage<>("aardvark");
		Message<?> message2 = new GenericMessage<>("bear");
		Message<?> message3 = new GenericMessage<>("cat");
		Message<?> message4 = new GenericMessage<>("dog");
		Message<?> message5 = new GenericMessage<>("elephant");
		this.referencedScriptInput.send(message1);
		this.referencedScriptInput.send(message2);
		this.referencedScriptInput.send(message3);
		this.referencedScriptInput.send(message4);
		this.referencedScriptInput.send(message5);
		assertEquals("cat", shortStrings.receive(0).getPayload());
		assertEquals("dog", shortStrings.receive(0).getPayload());
		assertEquals("aardvark", longStrings.receive(0).getPayload());
		assertEquals("bear", longStrings.receive(0).getPayload());
		assertEquals("elephant", longStrings.receive(0).getPayload());
		assertNull(shortStrings.receive(0));
		assertNull(longStrings.receive(0));
	}

	@Test
	public void inlineScript() { // long is > 5
		Message<?> message1 = new GenericMessage<>("aardvark");
		Message<?> message2 = new GenericMessage<>("bear");
		Message<?> message3 = new GenericMessage<>("cat");
		Message<?> message4 = new GenericMessage<>("dog");
		Message<?> message5 = new GenericMessage<>("elephant");
		this.inlineScriptInput.send(message1);
		this.inlineScriptInput.send(message2);
		this.inlineScriptInput.send(message3);
		this.inlineScriptInput.send(message4);
		this.inlineScriptInput.send(message5);
		assertEquals("bear", shortStrings.receive(0).getPayload());
		assertEquals("cat", shortStrings.receive(0).getPayload());
		assertEquals("dog", shortStrings.receive(0).getPayload());
		assertEquals("aardvark", longStrings.receive(0).getPayload());
		assertEquals("elephant", longStrings.receive(0).getPayload());
		assertNull(shortStrings.receive(0));
		assertNull(longStrings.receive(0));
	}

	@Test
	public void testInt2433VerifyRiddingOfMessageProcessorsWrapping() {
		assertTrue(this.groovyRouterMessageHandler instanceof MethodInvokingRouter);
		@SuppressWarnings("rawtypes")
		MessageProcessor messageProcessor = TestUtils.getPropertyValue(this.groovyRouterMessageHandler,
				"messageProcessor", MessageProcessor.class);
		//before it was MethodInvokingMessageProcessor
		assertTrue(messageProcessor instanceof GroovyScriptExecutingMessageProcessor);
	}

}

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

package org.springframework.integration.http.config;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.integration.test.util.TestUtils.getPropertyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.http.converter.SerializingHttpMessageConverter;
import org.springframework.integration.http.inbound.HttpRequestHandlingController;
import org.springframework.integration.http.inbound.HttpRequestHandlingMessagingGateway;
import org.springframework.integration.http.support.DefaultHttpHeaderMapper;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Mark Fisher
 * @author Iwein Fuld
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Gunnar Hillert
 * @author Biju Kunjummen
 * @author Artem Bilan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@DirtiesContext
public class HttpInboundGatewayParserTests {

	@Autowired
	@Qualifier("inboundGateway")
	private HttpRequestHandlingMessagingGateway gateway;

	@Autowired
	@Qualifier("inboundGatewayWithOneCustomConverter")
	private HttpRequestHandlingMessagingGateway gatewayWithOneCustomConverter;

	@Autowired
	@Qualifier("inboundGatewayNoDefaultConverters")
	private HttpRequestHandlingMessagingGateway gatewayNoDefaultConverters;

	@Autowired
	@Qualifier("inboundGatewayWithCustomAndDefaultConverters")
	private HttpRequestHandlingMessagingGateway gatewayWithCustomAndDefaultConverters;

	@Autowired
	@Qualifier("withMappedHeaders")
	private HttpRequestHandlingMessagingGateway withMappedHeaders;

	@Autowired
	@Qualifier("withMappedHeadersAndConverter")
	private HttpRequestHandlingMessagingGateway withMappedHeadersAndConverter;

	@Autowired
	private HttpRequestHandlingController inboundController;

	@Autowired
	private HttpRequestHandlingController inboundControllerViewExp;

	@Autowired
	private SubscribableChannel requests;

	@Autowired
	private PollableChannel responses;


	@Test
	public void checkConfig() {
		assertNotNull(this.gateway);
		assertTrue(getPropertyValue(this.gateway, "expectReply", Boolean.class));
		assertTrue(getPropertyValue(this.gateway, "convertExceptions", Boolean.class));
		assertSame(this.responses, getPropertyValue(this.gateway, "replyChannel"));
		assertNotNull(TestUtils.getPropertyValue(this.gateway, "errorChannel"));
		MessagingTemplate messagingTemplate =
				TestUtils.getPropertyValue(this.gateway, "messagingTemplate", MessagingTemplate.class);
		assertEquals(1234L, TestUtils.getPropertyValue(messagingTemplate, "sendTimeout"));
		assertEquals(4567L, TestUtils.getPropertyValue(messagingTemplate, "receiveTimeout"));

		boolean registerDefaultConverters =
				TestUtils.getPropertyValue(this.gateway, "mergeWithDefaultConverters", Boolean.class);
		assertFalse("By default the register-default-converters flag should be false", registerDefaultConverters);
		@SuppressWarnings("unchecked")
		List<HttpMessageConverter<?>> messageConverters =
				TestUtils.getPropertyValue(this.gateway, "messageConverters", List.class);

		assertTrue("The default converters should have been registered, given there are no custom converters",
				messageConverters.size() > 0);

		assertFalse(TestUtils.getPropertyValue(this.gateway, "autoStartup", Boolean.class));
		assertEquals(1001, TestUtils.getPropertyValue(this.gateway, "phase"));
	}

	@Test
	@DirtiesContext
	public void checkFlow() throws Exception {
		this.requests.subscribe(handlerExpecting(any(Message.class)));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("GET");
		request.addHeader("Accept", "application/my-serialized");
		request.setParameter("foo", "bar");

		MockHttpServletResponse response = new MockHttpServletResponse();
		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		SerializingHttpMessageConverter serializingHttpMessageConverter = new SerializingHttpMessageConverter();
		serializingHttpMessageConverter.setSupportedMediaTypes(
				Collections.singletonList(new MediaType("application", "my-serialized")));
		converters.add(serializingHttpMessageConverter);
		this.gateway.setMessageConverters(converters);
		this.gateway.afterPropertiesSet();
		this.gateway.start();

		this.gateway.handleRequest(request, response);
		assertThat(response.getStatus(), is(HttpServletResponse.SC_OK));

		assertEquals(response.getContentType(), "application/my-serialized");
	}

	@Test
	public void testController() {
		DirectFieldAccessor accessor = new DirectFieldAccessor(inboundController);
		String errorCode = (String) accessor.getPropertyValue("errorCode");
		assertEquals("oops", errorCode);
		LiteralExpression viewExpression = (LiteralExpression) accessor.getPropertyValue("viewExpression");
		assertEquals("foo", viewExpression.getValue());
	}

	@Test
	public void testControllerViewExp() {
		DirectFieldAccessor accessor = new DirectFieldAccessor(inboundControllerViewExp);
		String errorCode = (String) accessor.getPropertyValue("errorCode");
		assertEquals("oops", errorCode);
		SpelExpression viewExpression = (SpelExpression) accessor.getPropertyValue("viewExpression");
		assertNotNull(viewExpression);
		assertEquals("'bar'", viewExpression.getExpressionString());
	}

	@Test
	public void requestWithHeaders() {
		DefaultHttpHeaderMapper headerMapper =
				TestUtils.getPropertyValue(this.withMappedHeaders, "headerMapper", DefaultHttpHeaderMapper.class);

		HttpHeaders headers = new HttpHeaders();
		headers.set("foo", "foo");
		headers.set("bar", "bar");
		headers.set("baz", "baz");
		Map<String, Object> map = headerMapper.toHeaders(headers);
		assertEquals(2, map.size());
		assertEquals("foo", map.get("foo"));
		assertEquals("bar", map.get("bar"));

		Map<String, Object> mapOfHeaders = new HashMap<String, Object>();
		mapOfHeaders.put("abc", "abc");
		MessageHeaders mh = new MessageHeaders(mapOfHeaders);
		headers = new HttpHeaders();
		headerMapper.fromHeaders(mh, headers);
		assertEquals(1, headers.size());
		List<String> abc = headers.get("abc");
		assertEquals("abc", abc.get(0));
	}

	@Test
	public void requestWithHeadersWithConversionService() {
		DefaultHttpHeaderMapper headerMapper =
				TestUtils.getPropertyValue(this.withMappedHeadersAndConverter,
						"headerMapper", DefaultHttpHeaderMapper.class);

		headerMapper.setUserDefinedHeaderPrefix("X-");

		HttpHeaders headers = new HttpHeaders();
		headers.set("foo", "foo");
		headers.set("bar", "bar");
		headers.set("baz", "baz");
		Map<String, Object> map = headerMapper.toHeaders(headers);
		assertEquals(2, map.size());
		assertEquals("foo", map.get("foo"));
		assertEquals("bar", map.get("bar"));

		Map<String, Object> mapOfHeaders = new HashMap<>();
		mapOfHeaders.put("abc", "abc");
		Person person = new Person();
		person.setName("Oleg");
		mapOfHeaders.put("person", person);
		MessageHeaders mh = new MessageHeaders(mapOfHeaders);
		headers = new HttpHeaders();
		headerMapper.fromHeaders(mh, headers);
		assertEquals(2, headers.size());
		List<String> abc = headers.get("X-abc");
		assertEquals("abc", abc.get(0));
		List<String> personHeaders = headers.get("X-person");
		assertEquals("Oleg", personHeaders.get(0));
	}

	@Test
	public void testInboundGatewayWithMessageConverterDefaults() {
		@SuppressWarnings("unchecked")
		List<HttpMessageConverter<?>> messageConverters =
				TestUtils.getPropertyValue(this.gatewayWithOneCustomConverter, "messageConverters", List.class);
		assertThat("There should be only 1 message converter, by default register-default-converters is off",
				messageConverters.size(), is(1));

		//The converter should be the customized one
		assertThat(messageConverters.get(0), instanceOf(SerializingHttpMessageConverter.class));
	}

	@Test
	public void testInboundGatewayWithNoMessageConverterDefaults() {
		@SuppressWarnings("unchecked")
		List<HttpMessageConverter<?>> messageConverters =
				TestUtils.getPropertyValue(this.gatewayNoDefaultConverters, "messageConverters", List.class);
		//First converter should be the customized one
		assertThat(messageConverters.get(0), instanceOf(SerializingHttpMessageConverter.class));

		assertThat("There should be only 1 message converter, the register-default-converters is false",
				messageConverters.size(), is(1));
	}

	@Test
	public void testInboundGatewayWithCustomAndDefaultMessageConverters() {
		@SuppressWarnings("unchecked")
		List<HttpMessageConverter<?>> messageConverters =
				TestUtils.getPropertyValue(this.gatewayWithCustomAndDefaultConverters, "messageConverters",
						List.class);
		//First converter should be the customized one
		assertThat(messageConverters.get(0), instanceOf(SerializingHttpMessageConverter.class));

		assertTrue("There should be more than one converter",
				messageConverters.size() > 1);
	}

	@SuppressWarnings("rawtypes")
	private MessageHandler handlerExpecting(final Matcher<Message> messageMatcher) {
		return message -> assertThat(message, is(messageMatcher));
	}


	public static class Person {

		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

	public static class PersonConverter implements Converter<Person, String> {

		@Override
		public String convert(Person source) {
			return source.getName();
		}

	}

}

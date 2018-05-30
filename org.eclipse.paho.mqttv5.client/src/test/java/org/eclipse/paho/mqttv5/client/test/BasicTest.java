package org.eclipse.paho.mqttv5.client.test;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.mqttv5.client.IMqttDeliveryToken;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.test.logging.LoggingUtilities;
import org.eclipse.paho.mqttv5.client.test.properties.TestProperties;
import org.eclipse.paho.mqttv5.client.test.utilities.MqttV5Receiver;
import org.eclipse.paho.mqttv5.client.test.utilities.TestClientUtilities;
import org.eclipse.paho.mqttv5.client.test.utilities.Utility;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A series of basic connectivity tests to validate that basic functions work:
 * <ul>
 * <li>Connect</li>
 * <li>Subscribe</li>
 * <li>Publish</li>
 * <li>Unsubscribe</li>
 * <li>Disconnect</li>
 * </ul>
 *
 */
public class BasicTest {
	private static final Logger log = Logger.getLogger(BasicTest.class.getName());

	private static URI serverURI;
	private static String topicPrefix;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		try {
			String methodName = Utility.getMethodName();
			LoggingUtilities.banner(log, SubscribeTests.class, methodName);

			serverURI = TestProperties.getServerURI();
			topicPrefix = "BasicTest-" + UUID.randomUUID().toString() + "-";

		} catch (Exception exception) {
			log.log(Level.SEVERE, "caught exception:", exception);
			throw exception;
		}
	}

	/**
	 * Very simple test case that validates that the client can connect and then
	 * disconnect cleanly.
	 * 
	 * @throws MqttException
	 */
	@Test
	public void testConnectAndDisconnect() throws MqttException {
		String methodName = Utility.getMethodName();
		LoggingUtilities.banner(log, SubscribeTests.class, methodName);
		// This utility method already asserts that we are connected, so good so far
		MqttAsyncClient asyncClient = TestClientUtilities.connectAndGetClient(serverURI.toString(), methodName, null,
				null, 5000);
		// Client Should now be disconnected.
		TestClientUtilities.disconnectAndCloseClient(asyncClient, 5000);
	}

	/**
	 * Very simple test case that validates that once Forcibly disconnected, all
	 * threads are closed before returning control.
	 * 
	 * @throws MqttException
	 */
	@Test
	public void testCleanForciblyDisconnectThreads() throws MqttException {
		String methodName = Utility.getMethodName();
		LoggingUtilities.banner(log, SubscribeTests.class, methodName);
		// This utility method already asserts that we are connected, so good so far
		MqttAsyncClient asyncClient = TestClientUtilities.connectAndGetClient(serverURI.toString(), methodName, null,
				null, 5000);

		Set<Thread> clientThreads = Utility.getThreadsForClientId(methodName);
		log.info(String.format("After Connect, there are %d client threads running.",  clientThreads.size()));
		for (Thread thread : clientThreads) {
			log.info(" |- Thread: " + thread.getName());
		}

		Assert.assertEquals(4, clientThreads.size());

		log.info("Forcibly Disconnecting the client.");
		asyncClient.disconnectForcibly();

		Set<Thread> clientThreadsAfterDisconnect = Utility.getThreadsForClientId(methodName);

		log.info(String.format("After Forced Disconnect, there are %d client threads running.",  clientThreadsAfterDisconnect.size()));
		for (Thread thread : clientThreadsAfterDisconnect) {
			log.info(" |- Thread: " + thread.getName());
		}
		Assert.assertEquals(0, clientThreadsAfterDisconnect.size());
		asyncClient.close();
	}
	
	
	/**
	 * Very simple test case that validates that once normally disconnected, all
	 * threads are closed within a reasonable period of time.
	 * 
	 * @throws MqttException
	 * @throws InterruptedException 
	 */
	@Test
	public void testCleanDisconnectThreads() throws MqttException, InterruptedException {
		String methodName = Utility.getMethodName();
		LoggingUtilities.banner(log, SubscribeTests.class, methodName);
		// This utility method already asserts that we are connected, so good so far
		MqttAsyncClient asyncClient = TestClientUtilities.connectAndGetClient(serverURI.toString(), methodName, null,
				null, 5000);

		Set<Thread> clientThreads = Utility.getThreadsForClientId(methodName);
		log.info(String.format("After Connect, there are %d client threads running.",  clientThreads.size()));
		for (Thread thread : clientThreads) {
			log.info(" |- Thread: " + thread.getName());
		}

		Assert.assertEquals(4, clientThreads.size());

		log.info("Forcibly Disconnecting the client.");
		IMqttToken disconnectToken = asyncClient.disconnect();
		disconnectToken.waitForCompletion();
		
		// Wait for the threads to disappear
		Set<Thread> clientThreadsAfterDisconnect = Utility.getThreadsForClientId(methodName);
		log.info(String.format("Immediately after  Disconnect, there are %d client threads running. ",  clientThreadsAfterDisconnect.size()));
		
		// give it some time to reconnect
		long currentTime = System.currentTimeMillis();
		int timeout = 5000;
		while (clientThreadsAfterDisconnect.size() > 0) {
			long now = System.currentTimeMillis();
			if ((currentTime + timeout) < now) {
				log.warning("Timeout Exceeded");
				break;
			}
			clientThreadsAfterDisconnect = Utility.getThreadsForClientId(methodName);
			log.info(String.format("There are %d client threads running. ",  clientThreadsAfterDisconnect.size()));
			Thread.sleep(500);
		}
		log.info(String.format("There are %d client threads running. ",  clientThreadsAfterDisconnect.size()));
		for (Thread thread : clientThreadsAfterDisconnect) {
			log.info(" |- Thread: " + thread.getName());
		}
		Assert.assertEquals(0, clientThreadsAfterDisconnect.size());
		asyncClient.close();
	}

	/**
	 * Simple test case that validates that the client can Publish and Receive
	 * messages at QoS 0, 1 and 2
	 * 
	 * @throws MqttException,
	 *             InterruptedException
	 * 
	 */
	@Test
	public void testPublishAndReceive() throws MqttException, InterruptedException {
		String methodName = Utility.getMethodName();
		LoggingUtilities.banner(log, SubscribeTests.class, methodName);
		String topic = topicPrefix + methodName;

		int timeout = 5000;

		MqttV5Receiver mqttV5Receiver = new MqttV5Receiver(methodName, LoggingUtilities.getPrintStream());
		MqttAsyncClient asyncClient = TestClientUtilities.connectAndGetClient(serverURI.toString(), methodName,
				mqttV5Receiver, null, timeout);

		for (int qos = 0; qos <= 2; qos++) {
			log.info("Testing Publish and Receive at QoS: " + qos);
			// Subscribe to a topic
			log.info(String.format("Subscribing to: %s at QoS %d", topic, qos));
			MqttSubscription subscription = new MqttSubscription(topic, qos);
			IMqttToken subscribeToken = asyncClient.subscribe(subscription);
			subscribeToken.waitForCompletion(timeout);

			// Publish a message to the topic
			String messagePayload = "Test Payload at QoS : " + qos;
			MqttMessage testMessage = new MqttMessage(messagePayload.getBytes(), qos, false, null);
			log.info(String.format("Publishing Message %s to: %s at QoS: %d", testMessage.toDebugString(), topic, qos));
			IMqttDeliveryToken deliveryToken = asyncClient.publish(topic, testMessage);
			deliveryToken.waitForCompletion(timeout);

			log.info("Waiting for delivery and validating message.");
			boolean received = mqttV5Receiver.validateReceipt(topic, qos, testMessage);
			Assert.assertTrue(received);

			// Unsubscribe from the topic
			log.info("Unsubscribing from : " + topic);
			IMqttToken unsubscribeToken = asyncClient.unsubscribe(topic);
			unsubscribeToken.waitForCompletion(timeout);
		}
		TestClientUtilities.disconnectAndCloseClient(asyncClient, timeout);
	}

}

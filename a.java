// 1. ApiHandlerTest.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiHandlerTest {

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private ApiHandler apiHandler;

    @BeforeEach
    void setUp() {
        apiHandler = new ApiHandler();
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void testHandleRequest_Success() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        String requestBody = "{\"orderId\": \"order-123\"}";
        request.setBody(requestBody);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        request.setHeaders(headers);

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Order submitted successfully"));
        assertTrue(response.getBody().contains("order-123"));
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
    }

    @Test
    void testHandleRequest_InvalidJson() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        String invalidJson = "{invalid json}";
        request.setBody(invalidJson);

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertNotNull(response);
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Internal server error"));
    }

    @Test
    void testHandleRequest_NullBody() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(null);

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertNotNull(response);
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Internal server error"));
    }

    @Test
    void testHandleRequest_EmptyOrderId() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        String requestBody = "{\"orderId\": \"\"}";
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Order submitted successfully"));
        assertTrue(response.getBody().contains("\"\""));
    }

    @Test
    void testHandleRequest_MissingOrderId() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        String requestBody = "{\"otherField\": \"value\"}";
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Order submitted successfully"));
    }
}

// 2. ScheduledHandlerTest.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledHandlerTest {

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private ScheduledHandler scheduledHandler;

    @BeforeEach
    void setUp() {
        scheduledHandler = new ScheduledHandler();
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void testHandleRequest_Success() throws Exception {
        // Arrange
        ScheduledEvent event = createTestScheduledEvent();

        // Act
        String result = scheduledHandler.handleRequest(event, context);

        // Assert
        assertNotNull(result);
        assertEquals("Daily report generated successfully", result);
        verify(logger).log(anyString());
    }

    @Test
    void testHandleRequest_NullEvent() throws Exception {
        // Act
        String result = scheduledHandler.handleRequest(null, context);

        // Assert
        assertNotNull(result);
        assertEquals("Daily report generated successfully", result);
        verify(logger).log(anyString());
    }

    @Test
    void testHandleRequest_EventWithDetail() throws Exception {
        // Arrange
        ScheduledEvent event = createTestScheduledEvent();
        Map<String, Object> detail = new HashMap<>();
        detail.put("reportType", "daily");
        detail.put("department", "sales");
        event.setDetail(detail);

        // Act
        String result = scheduledHandler.handleRequest(event, context);

        // Assert
        assertNotNull(result);
        assertEquals("Daily report generated successfully", result);
        verify(logger).log(anyString());
    }

    @Test
    void testHandleRequest_VerifyLogging() throws Exception {
        // Arrange
        ScheduledEvent event = createTestScheduledEvent();

        // Act
        scheduledHandler.handleRequest(event, context);

        // Assert
        verify(logger).log("Scheduled event triggered at: " + event.getTime());
        verify(logger).log("Event ID: " + event.getId());
        verify(logger).log("Generating daily report...");
        verify(logger).log("Daily report generation completed");
    }

    private ScheduledEvent createTestScheduledEvent() {
        ScheduledEvent event = new ScheduledEvent();
        event.setId("cdc73f9d-aea9-11e3-9d5a-835b769c0d9c");
        event.setDetailType("Scheduled Event");
        event.setSource("aws.events");
        event.setAccount("123456789012");
        event.setTime(Instant.now());
        event.setRegion("us-east-1");
        event.setVersion("0");
        event.setResources(List.of("arn:aws:events:us-east-1:123456789012:rule/my-schedule"));
        
        return event;
    }
}

// 3. EventHandlerTest.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventHandlerTest {

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    @Mock
    private EventBridgeClient eventBridgeClient;

    private EventHandler eventHandler;

    @BeforeEach
    void setUp() {
        eventHandler = new EventHandler();
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void testHandleRequest_Success() {
        // Arrange
        Map<String, Object> input = createTestInput();
        
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(PutEventsResultEntry.builder()
                        .eventId("test-event-id")
                        .build())
                .build();

        try (MockedStatic<EventBridgeClient> mockedStatic = mockStatic(EventBridgeClient.class)) {
            mockedStatic.when(EventBridgeClient::create).thenReturn(eventBridgeClient);
            when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

            // Act
            String result = eventHandler.handleRequest(input, context);

            // Assert
            assertNotNull(result);
            assertEquals("Event published successfully", result);
            verify(eventBridgeClient).putEvents(any(PutEventsRequest.class));
            verify(logger).log(anyString());
        }
    }

    @Test
    void testHandleRequest_NullInput() {
        try (MockedStatic<EventBridgeClient> mockedStatic = mockStatic(EventBridgeClient.class)) {
            mockedStatic.when(EventBridgeClient::create).thenReturn(eventBridgeClient);

            // Act & Assert
            Exception exception = assertThrows(RuntimeException.class, () -> {
                eventHandler.handleRequest(null, context);
            });
            
            assertTrue(exception.getMessage().contains("Input cannot be null"));
        }
    }

    @Test
    void testHandleRequest_EventBridgeException() {
        // Arrange
        Map<String, Object> input = createTestInput();

        try (MockedStatic<EventBridgeClient> mockedStatic = mockStatic(EventBridgeClient.class)) {
            mockedStatic.when(EventBridgeClient::create).thenReturn(eventBridgeClient);
            when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                    .thenThrow(new RuntimeException("EventBridge error"));

            // Act & Assert
            Exception exception = assertThrows(RuntimeException.class, () -> {
                eventHandler.handleRequest(input, context);
            });
            
            assertTrue(exception.getMessage().contains("Failed to publish event"));
            verify(logger).log(anyString());
        }
    }

    @Test
    void testHandleRequest_EmptyInput() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(PutEventsResultEntry.builder()
                        .eventId("test-event-id")
                        .build())
                .build();

        try (MockedStatic<EventBridgeClient> mockedStatic = mockStatic(EventBridgeClient.class)) {
            mockedStatic.when(EventBridgeClient::create).thenReturn(eventBridgeClient);
            when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

            // Act
            String result = eventHandler.handleRequest(input, context);

            // Assert
            assertNotNull(result);
            assertEquals("Event published successfully", result);
            verify(eventBridgeClient).putEvents(any(PutEventsRequest.class));
        }
    }

    @Test
    void testHandleRequest_VerifyEventContent() {
        // Arrange
        Map<String, Object> input = createTestInput();
        
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(PutEventsResultEntry.builder()
                        .eventId("test-event-id")
                        .build())
                .build();

        try (MockedStatic<EventBridgeClient> mockedStatic = mockStatic(EventBridgeClient.class)) {
            mockedStatic.when(EventBridgeClient::create).thenReturn(eventBridgeClient);
            when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

            // Act
            eventHandler.handleRequest(input, context);

            // Assert
            verify(eventBridgeClient).putEvents(argThat(request -> {
                var entries = request.entries();
                assertFalse(entries.isEmpty());
                var entry = entries.get(0);
                assertEquals("order.submitted", entry.detailType());
                assertEquals("myapp.orders", entry.source());
                assertNotNull(entry.detail());
                return true;
            }));
        }
    }

    private Map<String, Object> createTestInput() {
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("customerId", "customer-456");
        input.put("amount", 99.99);
        input.put("status", "submitted");
        return input;
    }
}

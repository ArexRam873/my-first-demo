 我将为您生成三个Lambda类的单元测试，使用mock完成测试而不连接实际的AWS资源。
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>lambda-unit-tests</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- AWS Lambda Core -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-lambda-java-core</artifactId>
            <version>1.2.2</version>
        </dependency>

        <!-- AWS Lambda Events -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-lambda-java-events</artifactId>
            <version>3.11.0</version>
        </dependency>

        <!-- AWS SDK for Java V2 -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>eventbridge</artifactId>
            <version>2.20.26</version>
        </dependency>

        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>sfn</artifactId>
            <version>2.20.26</version>
        </dependency>

        <!-- Jackson for JSON processing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.15.2</version>
        </dependency>

        <!-- JUnit 5 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>5.9.2</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <version>5.9.2</version>
            <scope>test</scope>
        </dependency>

        <!-- Mockito -->
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>5.1.1</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <version>5.1.1</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-inline</artifactId>
            <version>5.1.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.0.0-M9</version>
                <configuration>
                    <useSystemClassLoader>false</useSystemClassLoader>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

```java
// 1. ApiHandler.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing API Gateway request");

        try {
            String requestBody = input.getBody();
            context.getLogger().log("Request body: " + requestBody);

            // Parse JSON request
            JsonNode jsonNode = objectMapper.readTree(requestBody);
            String orderId = jsonNode.has("orderId") ? jsonNode.get("orderId").asText() : "unknown";

            // Process the order
            String responseMessage = String.format("Order %s processed successfully", orderId);
            context.getLogger().log("Order processed: " + orderId);

            // Create response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", responseMessage);
            responseBody.put("orderId", orderId);
            responseBody.put("status", "success");

            return createResponse(200, responseBody);

        } catch (Exception e) {
            context.getLogger().log("Error processing request: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Internal server error");
            errorResponse.put("error", e.getMessage());
            return createResponse(500, errorResponse);
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Map<String, Object> body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        response.setHeaders(headers);

        try {
            response.setBody(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            response.setBody("{\"error\":\"Failed to serialize response\"}");
        }

        return response;
    }
}

// 2. EventBridgeHandler.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudWatchCustomMetricEvent;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.time.Instant;
import java.util.Map;

public class EventBridgeHandler implements RequestHandler<Map<String, Object>, String> {

    private final EventBridgeClient eventBridgeClient;

    public EventBridgeHandler() {
        this.eventBridgeClient = EventBridgeClient.builder().build();
    }

    // Constructor for testing with mock client
    public EventBridgeHandler(EventBridgeClient eventBridgeClient) {
        this.eventBridgeClient = eventBridgeClient;
    }

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        context.getLogger().log("Processing EventBridge event");

        try {
            String eventSource = (String) input.getOrDefault("source", "lambda.custom");
            String eventDetailType = (String) input.getOrDefault("detailType", "Custom Event");
            String eventDetail = input.toString();

            context.getLogger().log("Event source: " + eventSource);
            context.getLogger().log("Event detail type: " + eventDetailType);

            // Create EventBridge event
            PutEventsRequestEntry eventEntry = PutEventsRequestEntry.builder()
                    .source(eventSource)
                    .detailType(eventDetailType)
                    .detail(eventDetail)
                    .time(Instant.now())
                    .build();

            PutEventsRequest putEventsRequest = PutEventsRequest.builder()
                    .entries(eventEntry)
                    .build();

            // Send event to EventBridge
            PutEventsResponse response = eventBridgeClient.putEvents(putEventsRequest);
            
            context.getLogger().log("Event sent to EventBridge. Failed entry count: " + response.failedEntryCount());

            return "Event sent successfully to EventBridge";

        } catch (Exception e) {
            context.getLogger().log("Error sending event to EventBridge: " + e.getMessage());
            throw new RuntimeException("Failed to send event to EventBridge", e);
        }
    }
}

// 3. StepFunctionHandler.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import java.util.Map;
import java.util.UUID;

public class StepFunctionHandler implements RequestHandler<Map<String, Object>, String> {

    private final SfnClient sfnClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String stateMachineArn;

    public StepFunctionHandler() {
        this.sfnClient = SfnClient.builder().build();
        this.stateMachineArn = System.getenv("STATE_MACHINE_ARN");
    }

    // Constructor for testing with mock client
    public StepFunctionHandler(SfnClient sfnClient, String stateMachineArn) {
        this.sfnClient = sfnClient;
        this.stateMachineArn = stateMachineArn;
    }

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        context.getLogger().log("Starting Step Function execution");

        try {
            String inputJson = objectMapper.writeValueAsString(input);
            String executionName = "execution-" + UUID.randomUUID().toString();

            context.getLogger().log("State Machine ARN: " + stateMachineArn);
            context.getLogger().log("Execution name: " + executionName);
            context.getLogger().log("Input: " + inputJson);

            StartExecutionRequest request = StartExecutionRequest.builder()
                    .stateMachineArn(stateMachineArn)
                    .name(executionName)
                    .input(inputJson)
                    .build();

            StartExecutionResponse response = sfnClient.startExecution(request);
            
            context.getLogger().log("Step Function execution started: " + response.executionArn());

            return response.executionArn();

        } catch (Exception e) {
            context.getLogger().log("Error starting Step Function execution: " + e.getMessage());
            throw new RuntimeException("Failed to start Step Function execution", e);
        }
    }
}
```

```java
// 1. ApiHandlerTest.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiHandlerTest {

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private ApiHandler apiHandler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        apiHandler = new ApiHandler();
        objectMapper = new ObjectMapper();
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void testHandleRequest_Success() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        String requestBody = "{\"orderId\": \"order-123\", \"productId\": \"prod-456\"}";
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("success", responseBody.get("status"));
        assertEquals("order-123", responseBody.get("orderId"));
        assertTrue(responseBody.get("message").toString().contains("order-123"));
        
        // Verify headers
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        
        // Verify logging
        verify(logger, atLeastOnce()).log(anyString());
        verify(logger).log("Processing API Gateway request");
    }

    @Test
    void testHandleRequest_InvalidJson() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("{invalid json}");

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Internal server error"));
        
        verify(logger).log(contains("Error processing request"));
    }

    @Test
    void testHandleRequest_NullBody() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(null);

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        verify(logger).log(contains("Error processing request"));
    }

    @Test
    void testHandleRequest_MissingOrderId() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("{\"productId\": \"prod-456\"}");

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("unknown", responseBody.get("orderId"));
        assertTrue(responseBody.get("message").toString().contains("unknown"));
    }
}

// 2. EventBridgeHandlerTest.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventBridgeHandlerTest {

    @Mock
    private EventBridgeClient eventBridgeClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private EventBridgeHandler eventBridgeHandler;

    @BeforeEach
    void setUp() {
        eventBridgeHandler = new EventBridgeHandler(eventBridgeClient);
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void testHandleRequest_Success() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("source", "test.application");
        input.put("detailType", "Test Event");
        input.put("data", "test data");

        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();

        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

        // Act
        String result = eventBridgeHandler.handleRequest(input, context);

        // Assert
        assertEquals("Event sent successfully to EventBridge", result);
        
        verify(eventBridgeClient).putEvents(any(PutEventsRequest.class));
        verify(logger).log("Processing EventBridge event");
        verify(logger).log("Event source: test.application");
        verify(logger).log("Event detail type: Test Event");
        verify(logger).log("Event sent to EventBridge. Failed entry count: 0");
    }

    @Test
    void testHandleRequest_WithDefaultValues() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("data", "test data");

        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();

        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

        // Act
        String result = eventBridgeHandler.handleRequest(input, context);

        // Assert
        assertEquals("Event sent successfully to EventBridge", result);
        
        verify(logger).log("Event source: lambda.custom");
        verify(logger).log("Event detail type: Custom Event");
    }

    @Test
    void testHandleRequest_EventBridgeException() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("source", "test.application");

        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenThrow(new RuntimeException("EventBridge service error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            eventBridgeHandler.handleRequest(input, context);
        });

        assertEquals("Failed to send event to EventBridge", exception.getMessage());
        verify(logger).log(contains("Error sending event to EventBridge"));
    }

    @Test
    void testHandleRequest_EmptyInput() {
        // Arrange
        Map<String, Object> input = new HashMap<>();

        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();

        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

        // Act
        String result = eventBridgeHandler.handleRequest(input, context);

        // Assert
        assertEquals("Event sent successfully to EventBridge", result);
        verify(eventBridgeClient).putEvents(any(PutEventsRequest.class));
    }
}

// 3. StepFunctionHandlerTest.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepFunctionHandlerTest {

    @Mock
    private SfnClient sfnClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private StepFunctionHandler stepFunctionHandler;
    private final String testStateMachineArn = "arn:aws:states:us-east-1:123456789012:stateMachine:TestStateMachine";

    @BeforeEach
    void setUp() {
        stepFunctionHandler = new StepFunctionHandler(sfnClient, testStateMachineArn);
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void testHandleRequest_Success() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("customerId", "customer-456");

        String executionArn = "arn:aws:states:us-east-1:123456789012:execution:TestStateMachine:execution-12345";
        StartExecutionResponse mockResponse = StartExecutionResponse.builder()
                .executionArn(executionArn)
                .build();

        when(sfnClient.startExecution(any(StartExecutionRequest.class))).thenReturn(mockResponse);

        // Act
        String result = stepFunctionHandler.handleRequest(input, context);

        // Assert
        assertEquals(executionArn, result);
        
        verify(sfnClient).startExecution(any(StartExecutionRequest.class));
        verify(logger).log("Starting Step Function execution");
        verify(logger).log("State Machine ARN: " + testStateMachineArn);
        verify(logger).log(contains("Execution name: execution-"));
        verify(logger).log(contains("Input: {"));
        verify(logger).log("Step Function execution started: " + executionArn);
    }

    @Test
    void testHandleRequest_EmptyInput() {
        // Arrange
        Map<String, Object> input = new HashMap<>();

        String executionArn = "arn:aws:states:us-east-1:123456789012:execution:TestStateMachine:execution-12345";
        StartExecutionResponse mockResponse = StartExecutionResponse.builder()
                .executionArn(executionArn)
                .build();

        when(sfnClient.startExecution(any(StartExecutionRequest.class))).thenReturn(mockResponse);

        // Act
        String result = stepFunctionHandler.handleRequest(input, context);

        // Assert
        assertEquals(executionArn, result);
        verify(sfnClient).startExecution(any(StartExecutionRequest.class));
    }

    @Test
    void testHandleRequest_StepFunctionException() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");

        when(sfnClient.startExecution(any(StartExecutionRequest.class)))
                .thenThrow(new RuntimeException("Step Function service error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            stepFunctionHandler.handleRequest(input, context);
        });

        assertEquals("Failed to start Step Function execution", exception.getMessage());
        verify(logger).log(contains("Error starting Step Function execution"));
    }

    @Test
    void testHandleRequest_NullStateMachineArn() {
        // Arrange
        StepFunctionHandler handlerWithNullArn = new StepFunctionHandler(sfnClient, null);
        Map<String, Object> input = new HashMap<>();

        String executionArn = "arn:aws:states:us-east-1:123456789012:execution:TestStateMachine:execution-12345";
        StartExecutionResponse mockResponse = StartExecutionResponse.builder()
                .executionArn(executionArn)
                .build();

        when(sfnClient.startExecution(any(StartExecutionRequest.class))).thenReturn(mockResponse);

        // Act
        String result = handlerWithNullArn.handleRequest(input, context);

        // Assert
        assertEquals(executionArn, result);
        verify(logger).log("State Machine ARN: null");
    }

    @Test
    void testHandleRequest_ComplexInput() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("items", Map.of("item1", "value1", "item2", "value2"));
        input.put("metadata", Map.of("timestamp", "2023-12-01T10:00:00Z"));

        String executionArn = "arn:aws:states:us-east-1:123456789012:execution:TestStateMachine:execution-12345";
        StartExecutionResponse mockResponse = StartExecutionResponse.builder()
                .executionArn(executionArn)
                .build();

        when(sfnClient.startExecution(any(StartExecutionRequest.class))).thenReturn(mockResponse);

        // Act
        String result = stepFunctionHandler.handleRequest(input, context);

        // Assert
        assertEquals(executionArn, result);
        verify(sfnClient).startExecution(any(StartExecutionRequest.class));
        verify(logger).log(contains("Input: {"));
    }
}
```
我已经为您生成了完整的Maven依赖配置和三个Lambda处理器类的单元测试。以下是关键特点：

## Maven依赖特点：
- 使用JUnit 5作为测试框架
- 使用Mockito进行mock测试
- 包含AWS Lambda Core和Events库
- 包含AWS SDK V2的EventBridge和Step Functions客户端
- 包含Jackson用于JSON处理

## 单元测试特点：

### ApiHandlerTest：
- 测试正常请求处理流程
- 测试JSON解析错误处理
- 测试空请求体处理
- 测试缺少orderId字段的情况
- 验证响应格式和HTTP状态码
- 验证日志记录

### EventBridgeHandlerTest：
- 测试成功发送事件到EventBridge
- 测试使用默认值的情况
- 测试EventBridge客户端异常处理
- 测试空输入处理
- 使用mock EventBridgeClient，不连接真实AWS服务

### StepFunctionHandlerTest：
- 测试成功启动Step Function执行
- 测试空输入处理
- 测试Step Function客户端异常处理
- 测试null状态机ARN的情况
- 测试复杂输入数据处理
- 使用mock SfnClient，不连接真实AWS服务

## 运行测试：
```bash
mvn test
```

所有测试都使用mock对象，不会连接到实际的AWS资源，确保测试快速且独立运行。测试覆盖了基本的成功场景和异常处理场景。
 我将为您现有的三个Lambda函数生成完整的单元测试，使用mock数据而不连接真实的AWS资源。
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
        request.setBody("{\"orderId\":\"12345\",\"productName\":\"Test Product\"}");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        request.setHeaders(headers);

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("Order 12345 processed successfully", responseBody.get("message"));
        assertEquals("12345", responseBody.get("orderId"));
        assertEquals("success", responseBody.get("status"));
        
        // Verify headers
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        
        // Verify logging
        verify(logger, atLeastOnce()).log(anyString());
    }

    @Test
    void testHandleRequest_MissingOrderId() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("{\"productName\":\"Test Product\"}");

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals("Order unknown processed successfully", responseBody.get("message"));
        assertEquals("unknown", responseBody.get("orderId"));
    }

    @Test
    void testHandleRequest_InvalidJson() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("invalid json");

        // Act
        APIGatewayProxyResponseEvent response = apiHandler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify error logging
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
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.util.HashMap;
import java.util.List;
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
        input.put("eventData", "Sample event data");

        PutEventsResultEntry successEntry = PutEventsResultEntry.builder()
                .eventId("test-event-id-123")
                .build();

        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(List.of(successEntry))
                .failedEntryCount(0)
                .build();

        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

        // Act
        String result = eventBridgeHandler.handleRequest(input, context);

        // Assert
        assertEquals("Event published successfully with ID: test-event-id-123", result);
        
        // Verify EventBridge client interaction
        verify(eventBridgeClient).putEvents(any(PutEventsRequest.class));
        
        // Verify logging
        verify(logger).log("Processing EventBridge event");
        verify(logger).log("Event source: test.application");
        verify(logger).log("Event detail type: Test Event");
        verify(logger).log("Event published successfully");
    }

    @Test
    void testHandleRequest_DefaultValues() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("customData", "test data");

        PutEventsResultEntry successEntry = PutEventsResultEntry.builder()
                .eventId("default-event-id-456")
                .build();

        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(List.of(successEntry))
                .failedEntryCount(0)
                .build();

        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

        // Act
        String result = eventBridgeHandler.handleRequest(input, context);

        // Assert
        assertEquals("Event published successfully with ID: default-event-id-456", result);
        
        // Verify default values were used
        verify(logger).log("Event source: lambda.custom");
        verify(logger).log("Event detail type: Custom Event");
    }

    @Test
    void testHandleRequest_EventBridgeFailure() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("source", "test.application");

        PutEventsResultEntry failedEntry = PutEventsResultEntry.builder()
                .errorCode("ValidationException")
                .errorMessage("Event validation failed")
                .build();

        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(List.of(failedEntry))
                .failedEntryCount(1)
                .build();

        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

        // Act
        String result = eventBridgeHandler.handleRequest(input, context);

        // Assert
        assertEquals("Failed to publish event: ValidationException - Event validation failed", result);
        verify(logger).log(contains("Failed to publish event"));
    }

    @Test
    void testHandleRequest_Exception() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("source", "test.application");

        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenThrow(new RuntimeException("EventBridge service error"));

        // Act
        String result = eventBridgeHandler.handleRequest(input, context);

        // Assert
        assertEquals("Error publishing event: EventBridge service error", result);
        verify(logger).log(contains("Error publishing event"));
    }
}

// 3. StepFunctionHandlerTest.java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        stepFunctionHandler = new StepFunctionHandler(sfnClient);
        objectMapper = new ObjectMapper();
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void testHandleRequest_Success() throws Exception {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("workflowType", "order-processing");
        input.put("orderId", "ORDER-12345");
        input.put("customerId", "CUST-67890");

        StartExecutionResponse mockResponse = StartExecutionResponse.builder()
                .executionArn("arn:aws:states:us-east-1:123456789012:execution:order-workflow:test-execution-123")
                .startDate(java.time.Instant.now())
                .build();

        when(sfnClient.startExecution(any(StartExecutionRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, Object> result = stepFunctionHandler.handleRequest(input, context);

        // Assert
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("arn:aws:states:us-east-1:123456789012:execution:order-workflow:test-execution-123", 
                    result.get("executionArn"));
        assertNotNull(result.get("executionName"));
        assertTrue(((String) result.get("executionName")).startsWith("execution-"));

        // Verify Step Functions client interaction
        verify(sfnClient).startExecution(any(StartExecutionRequest.class));
        
        // Verify logging
        verify(logger).log("Processing Step Function trigger");
        verify(logger).log(contains("Workflow type: order-processing"));
        verify(logger).log(contains("Starting Step Function execution"));
        verify(logger).log(contains("Step Function execution started successfully"));
    }

    @Test
    void testHandleRequest_DefaultWorkflowType() throws Exception {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "ORDER-99999");

        StartExecutionResponse mockResponse = StartExecutionResponse.builder()
                .executionArn("arn:aws:states:us-east-1:123456789012:execution:default-workflow:test-execution-456")
                .startDate(java.time.Instant.now())
                .build();

        when(sfnClient.startExecution(any(StartExecutionRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, Object> result = stepFunctionHandler.handleRequest(input, context);

        // Assert
        assertEquals("success", result.get("status"));
        verify(logger).log("Workflow type: default-workflow");
    }

    @Test
    void testHandleRequest_StepFunctionFailure() {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("workflowType", "invalid-workflow");
        input.put("orderId", "ORDER-ERROR");

        when(sfnClient.startExecution(any(StartExecutionRequest.class)))
                .thenThrow(new RuntimeException("StateMachine does not exist"));

        // Act
        Map<String, Object> result = stepFunctionHandler.handleRequest(input, context);

        // Assert
        assertEquals("error", result.get("status"));
        assertEquals("StateMachine does not exist", result.get("error"));
        assertNull(result.get("executionArn"));
        
        // Verify error logging
        verify(logger).log(contains("Error starting Step Function execution"));
    }

    @Test
    void testHandleRequest_EmptyInput() throws Exception {
        // Arrange
        Map<String, Object> input = new HashMap<>();

        StartExecutionResponse mockResponse = StartExecutionResponse.builder()
                .executionArn("arn:aws:states:us-east-1:123456789012:execution:default-workflow:empty-input-execution")
                .startDate(java.time.Instant.now())
                .build();

        when(sfnClient.startExecution(any(StartExecutionRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, Object> result = stepFunctionHandler.handleRequest(input, context);

        // Assert
        assertEquals("success", result.get("status"));
        verify(logger).log("Workflow type: default-workflow");
    }

    @Test
    void testHandleRequest_NullInput() {
        // Arrange
        Map<String, Object> input = null;

        // Act
        Map<String, Object> result = stepFunctionHandler.handleRequest(input, context);

        // Assert
        assertEquals("error", result.get("status"));
        assertNotNull(result.get("error"));
        verify(logger).log(contains("Error starting Step Function execution"));
    }

    @Test
    void testExecutionNameGeneration() throws Exception {
        // Arrange
        Map<String, Object> input = new HashMap<>();
        input.put("workflowType", "test-workflow");

        StartExecutionResponse mockResponse = StartExecutionResponse.builder()
                .executionArn("arn:aws:states:us-east-1:123456789012:execution:test-workflow:generated-name")
                .startDate(java.time.Instant.now())
                .build();

        when(sfnClient.startExecution(any(StartExecutionRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, Object> result1 = stepFunctionHandler.handleRequest(input, context);
        Map<String, Object> result2 = stepFunctionHandler.handleRequest(input, context);

        // Assert - Execution names should be different
        assertNotEquals(result1.get("executionName"), result2.get("executionName"));
        assertTrue(((String) result1.get("executionName")).startsWith("execution-"));
        assertTrue(((String) result2.get("executionName")).startsWith("execution-"));
    }
}

// 4. Maven Surefire Plugin配置用于运行测试
/*
在pom.xml中添加以下plugin配置：

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.0.0-M9</version>
    <configuration>
        <useSystemClassLoader>false</useSystemClassLoader>
        <includes>
            <include>**/*Test.java</include>
        </includes>
    </configuration>
</plugin>

运行测试命令：
mvn test
或者运行特定测试类：
mvn test -Dtest=ApiHandlerTest
mvn test -Dtest=EventBridgeHandlerTest  
mvn test -Dtest=StepFunctionHandlerTest
*/
```
我已经为您的三个Lambda函数生成了完整的单元测试。这些测试具有以下特点：

## 测试覆盖范围

### 1. ApiHandlerTest
- ✅ 成功处理请求场景
- ✅ 缺少orderId的处理
- ✅ 无效JSON的错误处理
- ✅ 空请求体的错误处理
- ✅ 响应格式和状态码验证
- ✅ CORS头部设置验证

### 2. EventBridgeHandlerTest
- ✅ 成功发布事件到EventBridge
- ✅ 使用默认值的场景
- ✅ EventBridge服务失败处理
- ✅ 异常处理
- ✅ 日志记录验证

### 3. StepFunctionHandlerTest
- ✅ 成功启动Step Function执行
- ✅ 默认工作流类型处理
- ✅ Step Function失败处理
- ✅ 空输入和null输入处理
- ✅ 执行名称唯一性验证

## Mock使用特点

1. **完全Mock化**: 所有AWS服务客户端都使用Mock，不会连接真实AWS资源
2. **Context和Logger Mock**: Lambda运行时环境完全模拟
3. **响应数据Mock**: 使用真实的AWS SDK响应对象结构
4. **异常场景Mock**: 模拟各种异常和错误情况

## 运行测试

使用以下命令运行测试：

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=ApiHandlerTest
mvn test -Dtest=EventBridgeHandlerTest
mvn test -Dtest=StepFunctionHandlerTest

# 运行测试并生成报告
mvn test -Dmaven.test.failure.ignore=true
```

这些测试确保您的Lambda函数在各种情况下都能正确工作，同时不需要任何真实的AWS资源连接。
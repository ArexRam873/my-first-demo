 这个问题是因为在单元测试中，Mock的Context对象的getLogger()方法返回了null。我来提供几种解决方案：
```java
// =================================================
// 方案1：修改ApiHandler，增加空值检查（推荐）
// =================================================

public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final EventBridgeClient eventBridgeClient;
    
    // 默认构造函数，用于Lambda运行时
    public ApiHandler() {
        this.eventBridgeClient = EventBridgeClient.create();
    }
    
    // 测试用构造函数
    public ApiHandler(EventBridgeClient eventBridgeClient) {
        this.eventBridgeClient = eventBridgeClient;
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // 安全的日志记录
            if (context != null && context.getLogger() != null) {
                context.getLogger().log("Received API Gateway request: " + input.getBody());
            }
            
            // 解析请求
            String requestBody = input.getBody();
            
            // 构建EventBridge事件
            PutEventsRequestEntry event = PutEventsRequestEntry.builder()
                    .source("custom.application")
                    .detailType("API Request")
                    .detail(requestBody)
                    .build();
            
            PutEventsRequest request = PutEventsRequest.builder()
                    .entries(event)
                    .build();
            
            // 发送事件
            PutEventsResponse response = eventBridgeClient.putEvents(request);
            
            if (context != null && context.getLogger() != null) {
                context.getLogger().log("Event sent successfully with ID: " + 
                    response.entries().get(0).eventId());
            }
            
            // 返回成功响应
            return APIGatewayProxyResponseEvent.builder()
                    .statusCode(200)
                    .body("{\"message\":\"Event sent successfully\",\"eventId\":\"" + 
                          response.entries().get(0).eventId() + "\"}")
                    .headers(Map.of("Content-Type", "application/json"))
                    .build();
                    
        } catch (Exception e) {
            // 安全的错误日志记录
            if (context != null && context.getLogger() != null) {
                context.getLogger().log("Error processing request: " + e.getMessage());
            }
            
            // 错误处理
            return APIGatewayProxyResponseEvent.builder()
                    .statusCode(500)
                    .body("{\"error\":\"" + e.getMessage() + "\"}")
                    .headers(Map.of("Content-Type", "application/json"))
                    .build();
        }
    }
}

// =================================================
// 方案2：修改单元测试，Mock LambdaLogger
// =================================================

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiHandlerTest {
    
    @Mock
    private EventBridgeClient eventBridgeClient;
    
    @Mock
    private Context context;
    
    @Mock
    private LambdaLogger lambdaLogger;
    
    @Test
    void testHandleRequest_Success() {
        // 准备测试数据
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("{\"message\":\"test data\"}");
        
        // Mock Context和Logger
        when(context.getLogger()).thenReturn(lambdaLogger);
        
        // Mock EventBridge响应
        PutEventsResultEntry resultEntry = PutEventsResultEntry.builder()
                .eventId("test-event-id-123")
                .build();
        
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(resultEntry)
                .build();
        
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);
        
        // 创建Handler实例
        ApiHandler handler = new ApiHandler(eventBridgeClient);
        
        // 执行测试
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        
        // 验证结果
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Event sent successfully"));
        assertTrue(response.getBody().contains("test-event-id-123"));
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // 验证日志调用
        verify(lambdaLogger, atLeastOnce()).log(anyString());
        verify(lambdaLogger).log("Received API Gateway request: {\"message\":\"test data\"}");
        verify(lambdaLogger).log("Event sent successfully with ID: test-event-id-123");
        
        // 验证EventBridge调用
        verify(eventBridgeClient, times(1)).putEvents(any(PutEventsRequest.class));
    }
    
    @Test
    void testHandleRequest_EventBridgeException() {
        // 准备测试数据
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("{\"message\":\"test data\"}");
        
        // Mock Context和Logger
        when(context.getLogger()).thenReturn(lambdaLogger);
        
        // Mock EventBridge抛出异常
        RuntimeException testException = new RuntimeException("EventBridge service error");
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenThrow(testException);
        
        // 创建Handler实例
        ApiHandler handler = new ApiHandler(eventBridgeClient);
        
        // 执行测试
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        
        // 验证错误响应
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("EventBridge service error"));
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // 验证错误日志调用
        verify(lambdaLogger).log("Received API Gateway request: {\"message\":\"test data\"}");
        verify(lambdaLogger).log("Error processing request: EventBridge service error");
        
        // 验证EventBridge调用
        verify(eventBridgeClient, times(1)).putEvents(any(PutEventsRequest.class));
    }
    
    @Test
    void testHandleRequest_NullBody() {
        // 准备测试数据
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(null);
        
        // Mock Context和Logger
        when(context.getLogger()).thenReturn(lambdaLogger);
        
        // Mock EventBridge响应
        PutEventsResultEntry resultEntry = PutEventsResultEntry.builder()
                .eventId("test-event-id-456")
                .build();
        
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(resultEntry)
                .build();
        
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);
        
        // 创建Handler实例
        ApiHandler handler = new ApiHandler(eventBridgeClient);
        
        // 执行测试
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        
        // 验证结果
        assertEquals(200, response.getStatusCode());
        
        // 验证日志调用
        verify(lambdaLogger).log("Received API Gateway request: null");
        
        // 验证EventBridge调用参数
        verify(eventBridgeClient).putEvents(argThat(putEventsRequest -> {
            PutEventsRequestEntry entry = putEventsRequest.entries().get(0);
            return entry.detail() == null && 
                   "custom.application".equals(entry.source()) &&
                   "API Request".equals(entry.detailType());
        }));
    }
    
    @Test
    void testHandleRequest_VerifyEventDetails() {
        // 准备测试数据
        String testBody = "{\"userId\":123,\"action\":\"create\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(testBody);
        
        // Mock Context和Logger
        when(context.getLogger()).thenReturn(lambdaLogger);
        
        // Mock EventBridge响应
        PutEventsResultEntry resultEntry = PutEventsResultEntry.builder()
                .eventId("test-event-id-789")
                .build();
        
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(resultEntry)
                .build();
        
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);
        
        // 创建Handler实例
        ApiHandler handler = new ApiHandler(eventBridgeClient);
        
        // 执行测试
        handler.handleRequest(request, context);
        
        // 验证日志调用
        verify(lambdaLogger).log("Received API Gateway request: " + testBody);
        
        // 详细验证EventBridge调用参数
        verify(eventBridgeClient).putEvents(argThat(putEventsRequest -> {
            PutEventsRequestEntry entry = putEventsRequest.entries().get(0);
            return testBody.equals(entry.detail()) && 
                   "custom.application".equals(entry.source()) &&
                   "API Request".equals(entry.detailType());
        }));
    }
    
    @Test
    void testHandleRequest_WithoutLogger() {
        // 测试当Context为null或Logger为null时的情况
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("{\"message\":\"test without logger\"}");
        
        // 不设置Context或设置返回null的Logger
        when(context.getLogger()).thenReturn(null);
        
        // Mock EventBridge响应
        PutEventsResultEntry resultEntry = PutEventsResultEntry.builder()
                .eventId("test-event-id-no-logger")
                .build();
        
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .entries(resultEntry)
                .build();
        
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);
        
        // 创建Handler实例
        ApiHandler handler = new ApiHandler(eventBridgeClient);
        
        // 执行测试 - 应该不会抛出异常
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        
        // 验证结果
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Event sent successfully"));
        
        // 验证没有日志调用（因为logger为null）
        verify(lambdaLogger, never()).log(anyString());
        
        // 验证EventBridge调用
        verify(eventBridgeClient, times(1)).putEvents(any(PutEventsRequest.class));
    }
}

// =================================================
// 方案3：创建测试用的Logger实现类
// =================================================

class TestLambdaLogger implements LambdaLogger {
    private final java.util.List<String> logs = new java.util.ArrayList<>();
    
    @Override
    public void log(String message) {
        logs.add(message);
        System.out.println("LOG: " + message);
    }
    
    @Override
    public void log(byte[] message) {
        String msg = new String(message);
        logs.add(msg);
        System.out.println("LOG: " + msg);
    }
    
    public java.util.List<String> getLogs() {
        return new java.util.ArrayList<>(logs);
    }
    
    public void clearLogs() {
        logs.clear();
    }
}

// 使用TestLambdaLogger的测试示例
@Test
void testHandleRequest_WithTestLogger() {
    // 准备测试数据
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setBody("{\"message\":\"test data\"}");
    
    // 创建测试Logger
    TestLambdaLogger testLogger = new TestLambdaLogger();
    when(context.getLogger()).thenReturn(testLogger);
    
    // Mock EventBridge响应
    PutEventsResultEntry resultEntry = PutEventsResultEntry.builder()
            .eventId("test-event-id-123")
            .build();
    
    PutEventsResponse mockResponse = PutEventsResponse.builder()
            .entries(resultEntry)
            .build();
    
    when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
            .thenReturn(mockResponse);
    
    // 创建Handler实例
    ApiHandler handler = new ApiHandler(eventBridgeClient);
    
    // 执行测试
    APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
    
    // 验证结果
    assertEquals(200, response.getStatusCode());
    
    // 验证日志内容
    java.util.List<String> logs = testLogger.getLogs();
    assertTrue(logs.stream().anyMatch(log -> log.contains("Received API Gateway request")));
    assertTrue(logs.stream().anyMatch(log -> log.contains("Event sent successfully with ID")));
}
```
## 问题分析

这个NullPointerException的根本原因是：
1. 在单元测试中，`@Mock`注解创建的Mock对象默认情况下所有方法都返回null
2. `context.getLogger()`返回null，导致调用`log()`方法时抛出NPE

## 推荐解决方案

**方案1（推荐）：修改ApiHandler增加空值检查**
- 这是最安全的做法，在实际Lambda环境中也能避免潜在的NPE
- 代码更加健壮，适合生产环境

**方案2：在测试中正确Mock LambdaLogger**
- 适合想要验证日志调用的场景
- 需要额外Mock LambdaLogger对象

**方案3：创建测试用的Logger实现**
- 可以实际查看日志输出内容
- 适合需要验证具体日志内容的测试场景

## 修改要点

1. **空值检查**：在调用`context.getLogger().log()`之前先检查context和logger是否为null
2. **Mock设置**：在测试中正确设置`when(context.getLogger()).thenReturn(lambdaLogger)`
3. **验证日志**：可以验证日志方法的调用次数和参数

选择方案1是最佳实践，因为它既解决了测试问题，又提高了代码的健壮性。
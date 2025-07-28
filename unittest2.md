 好的，我来为你提供完整的方案1实现代码。
```java
// 首先修改ApiHandler类，使其支持依赖注入
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
            
            // 返回成功响应
            return APIGatewayProxyResponseEvent.builder()
                    .statusCode(200)
                    .body("{\"message\":\"Event sent successfully\",\"eventId\":\"" + 
                          response.entries().get(0).eventId() + "\"}")
                    .headers(Map.of("Content-Type", "application/json"))
                    .build();
                    
        } catch (Exception e) {
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
// 单元测试类
// =================================================

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiHandlerTest {
    
    @Mock
    private EventBridgeClient eventBridgeClient;
    
    @Mock
    private Context context;
    
    @Test
    void testHandleRequest_Success() {
        // 准备测试数据
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("{\"message\":\"test data\"}");
        
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
        
        // 验证EventBridge调用
        verify(eventBridgeClient, times(1)).putEvents(any(PutEventsRequest.class));
    }
    
    @Test
    void testHandleRequest_EventBridgeException() {
        // 准备测试数据
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("{\"message\":\"test data\"}");
        
        // Mock EventBridge抛出异常
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenThrow(new RuntimeException("EventBridge service error"));
        
        // 创建Handler实例
        ApiHandler handler = new ApiHandler(eventBridgeClient);
        
        // 执行测试
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        
        // 验证错误响应
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("EventBridge service error"));
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        
        // 验证EventBridge调用
        verify(eventBridgeClient, times(1)).putEvents(any(PutEventsRequest.class));
    }
    
    @Test
    void testHandleRequest_NullBody() {
        // 准备测试数据
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(null);
        
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
        
        // 详细验证EventBridge调用参数
        verify(eventBridgeClient).putEvents(argThat(putEventsRequest -> {
            PutEventsRequestEntry entry = putEventsRequest.entries().get(0);
            return testBody.equals(entry.detail()) && 
                   "custom.application".equals(entry.source()) &&
                   "API Request".equals(entry.detailType());
        }));
    }
}

// =================================================
// Maven依赖配置 (pom.xml)
// =================================================

/*
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
    
    <!-- AWS SDK EventBridge -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>eventbridge</artifactId>
        <version>2.20.56</version>
    </dependency>
    
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
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
    
    <!-- Mockito JUnit Jupiter -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <version>5.1.1</version>
        <scope>test</scope>
    </dependency>
</dependencies>
*/
```
这个完整的Mock实现包含以下关键点：

## 主要特性

1. **构造函数注入**：ApiHandler支持两个构造函数，默认构造函数用于Lambda运行时，带参数构造函数用于测试

2. **完整的Mock测试**：
   - 成功场景测试
   - 异常场景测试  
   - 空请求体测试
   - 详细参数验证测试

3. **使用@ExtendWith(MockitoExtension.class)**：自动初始化Mock对象

## 测试覆盖

- ✅ 正常流程测试
- ✅ 异常处理测试
- ✅ 边界条件测试（null body）
- ✅ 参数传递验证
- ✅ AWS服务调用验证

## 关键优势

1. **无需真实AWS连接**：完全使用Mock，测试运行快速
2. **隔离性好**：只测试业务逻辑，不依赖外部服务
3. **可控性强**：可以模拟各种异常场景
4. **易于维护**：测试代码简洁清晰

这样你就可以在没有AWS区域配置的情况下顺利运行单元测试了。有任何问题或需要调整的地方吗？
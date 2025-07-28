AWS用户注册处理系统Demo
下面我再提供一个用户注册处理系统的Demo，使用相同的四个AWS组件：API Gateway、Lambda、Step Functions和EventBridge。这个场景模拟用户注册流程，包含信息验证、账户创建和通知发送。
业务场景
用户通过API提交注册信息 -> 验证用户数据 -> 创建用户账户 -> 发送欢迎通知 -> 发布注册完成事件
项目结构
user-registration-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/example/
│   │   │   ├── RegistrationHandler.java    # API Gateway触发的Lambda
│   │   │   ├── ValidateUserData.java       # Step Functions步骤1
│   │   │   ├── CreateUserAccount.java      # Step Functions步骤2
│   │   │   ├── SendWelcomeNotification.java # Step Functions步骤3
│   │   │   └── PublishRegistrationEvent.java # EventBridge发布
│   │   └── resources/
│   │       └── registration-workflow.asl.json # Step Function定义
│   └── test/
│       └── java/com/example/
│           └── RegistrationHandlerTest.java # 单元测试
└── template.yml                           # SAM部署模板

代码实现
pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>user-registration-demo</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <aws.sdk.version>2.20.0</aws.sdk.version>
        <aws.lambda.version>1.3.2</aws.lambda.version>
    </properties>

    <dependencies>
        <!-- AWS SDK -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>sfn</artifactId>
            <version>${aws.sdk.version}</version>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>eventbridge</artifactId>
            <version>${aws.sdk.version}</version>
        </dependency>
        <!-- Lambda Core -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-lambda-java-core</artifactId>
            <version>${aws.lambda.version}</version>
        </dependency>
        <!-- JSON Processing -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.10.1</version>
        </dependency>
        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>5.3.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>

Lambda 处理程序
RegistrationHandler.java
package com.example;

import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import java.util.Map;
import java.util.UUID;

public class RegistrationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private static final String STATE_MACHINE_ARN = System.getenv("STATE_MACHINE_ARN");
    private final SfnClient sfnClient = SfnClient.create();
    private final Gson gson = new Gson();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        // 解析注册数据
        Map<String, String> userData = gson.fromJson(input.getBody(), Map.class);
        String registrationId = UUID.randomUUID().toString();
        
        // 启动Step Function
        StartExecutionRequest request = StartExecutionRequest.builder()
                .stateMachineArn(STATE_MACHINE_ARN)
                .input(gson.toJson(Map.of(
                    "registrationId", registrationId,
                    "userData", userData
                )))
                .build();
        
        sfnClient.startExecution(request);
        
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(202)
                .withBody("{\"status\":\"PROCESSING\", \"registrationId\":\"" + registrationId + "\"}");
    }
}

ValidateUserData.java
package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

public class ValidateUserData implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String registrationId = (String) input.get("registrationId");
        Map<String, String> userData = (Map<String, String>) input.get("userData");
        
        System.out.println("Validating registration: " + registrationId);
        
        // 简单验证逻辑
        if (!userData.containsKey("email") || !userData.get("email").contains("@")) {
            throw new RuntimeException("Invalid email address");
        }
        if (!userData.containsKey("password") || userData.get("password").length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters");
        }
        
        // 添加验证结果
        input.put("validationPassed", true);
        return input;
    }
}

CreateUserAccount.java
package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;
import java.util.UUID;

public class CreateUserAccount implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String registrationId = (String) input.get("registrationId");
        Map<String, String> userData = (Map<String, String>) input.get("userData");
        
        System.out.println("Creating account for: " + userData.get("email"));
        
        // 模拟账户创建
        String userId = UUID.randomUUID().toString();
        
        // 添加创建结果
        input.put("userId", userId);
        input.put("accountCreated", true);
        return input;
    }
}

SendWelcomeNotification.java
package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

public class SendWelcomeNotification implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String userId = (String) input.get("userId");
        Map<String, String> userData = (Map<String, String>) input.get("userData");
        String email = userData.get("email");
        
        System.out.println("Sending welcome notification to: " + email);
        
        // 模拟发送通知
        input.put("notificationSent", true);
        input.put("notificationType", "WELCOME_EMAIL");
        return input;
    }
}

PublishRegistrationEvent.java
package com.example;

import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import java.util.Map;

public class PublishRegistrationEvent implements RequestHandler<Map<String, Object>, Void> {
    
    private static final String EVENT_BUS_NAME = System.getenv("EVENT_BUS_NAME");
    private final EventBridgeClient eventBridge = EventBridgeClient.create();
    private final Gson gson = new Gson();

    @Override
    public Void handleRequest(Map<String, Object> input, Context context) {
        String registrationId = (String) input.get("registrationId");
        String userId = (String) input.get("userId");
        Map<String, String> userData = (Map<String, String>) input.get("userData");
        
        // 构建事件
        Map<String, Object> eventDetail = Map.of(
            "registrationId", registrationId,
            "userId", userId,
            "email", userData.get("email"),
            "status", "REGISTERED"
        );
        
        // 发送到EventBridge
        PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                .source("user.registration")
                .detailType("UserRegistrationCompleted")
                .eventBusName(EVENT_BUS_NAME)
                .detail(gson.toJson(eventDetail))
                .build();
        
        eventBridge.putEvents(PutEventsRequest.builder()
                .entries(entry)
                .build());
        
        System.out.println("Registration event published for user: " + userId);
        return null;
    }
}

Step Function 定义 (registration-workflow.asl.json)
{
  "Comment": "User Registration Workflow",
  "StartAt": "ValidateUserData",
  "States": {
    "ValidateUserData": {
      "Type": "Task",
      "Resource": "${ValidateUserDataFunctionArn}",
      "Next": "CreateUserAccount",
      "Catch": [
        {
          "ErrorEquals": ["States.ALL"],
          "Next": "RegistrationFailed"
        }
      ]
    },
    "CreateUserAccount": {
      "Type": "Task",
      "Resource": "${CreateUserAccountFunctionArn}",
      "Next": "SendWelcomeNotification"
    },
    "SendWelcomeNotification": {
      "Type": "Task",
      "Resource": "${SendWelcomeNotificationFunctionArn}",
      "Next": "PublishRegistrationEvent"
    },
    "PublishRegistrationEvent": {
      "Type": "Task",
      "Resource": "${PublishRegistrationEventFunctionArn}",
      "End": true
    },
    "RegistrationFailed": {
      "Type": "Fail",
      "Cause": "User registration validation failed",
      "Error": "ValidationError"
    }
  }
}

单元测试 (RegistrationHandlerTest.java)
package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class RegistrationHandlerTest {

    @Test
    void testHandleRequest() {
        // 模拟环境变量
        System.setProperty("STATE_MACHINE_ARN", "arn:aws:states:us-east-1:123456789012:stateMachine:RegistrationWorkflow");
        
        // 准备输入
        String requestBody = "{\"email\":\"user@example.com\",\"password\":\"secure1234\",\"name\":\"John Doe\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(requestBody);
        
        // 模拟SFn客户端
        try (MockedStatic<SfnClient> mockedSfn = mockStatic(SfnClient.class)) {
            SfnClient mockClient = Mockito.mock(SfnClient.class);
            mockedSfn.when(SfnClient::create).thenReturn(mockClient);
            
            StartExecutionResponse mockResponse = StartExecutionResponse.builder()
                    .executionArn("arn:aws:states:us-east-1:123456789012:execution:RegistrationWorkflow:exec1")
                    .build();
            Mockito.when(mockClient.startExecution(any())).thenReturn(mockResponse);
            
            // 执行测试
            RegistrationHandler handler = new RegistrationHandler();
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, Mockito.mock(Context.class));
            
            // 验证结果
            assertEquals(202, response.getStatusCode());
            assertTrue(response.getBody().contains("PROCESSING"));
            assertTrue(response.getBody().contains("registrationId"));
        }
    }
}

部署文档 (deploy.md)
# AWS用户注册系统部署指南

## 前置条件
1. AWS CLI已安装并配置凭证
2. SAM CLI已安装
3. Maven 3.9+ 和 Java 21


## 部署步骤

### 1. 构建项目
```bash
mvn clean package

2. 部署资源
sam deploy --guided

按提示输入：
- Stack Name: user-registration-demo
- AWS Region: 选择区域 (如us-east-1)
- 确认其他选项默认
3. 配置环境变量
部署完成后设置Lambda环境变量：
- STATE_MACHINE_ARN: Step Function ARN
- EVENT_BUS_NAME: EventBridge总线名称 (默认default)
4. 测试API
获取API Gateway端点：
aws cloudformation describe-stacks --stack-name user-registration-demo --query "Stacks[0].Outputs"

发送测试注册请求：
curl -X POST https://<api-id>.execute-api.<region>.amazonaws.com/Prod/register \
  -H "Content-Type: application/json" \
  -d '{
        "email": "user@example.com",
        "password": "securePassword123",
        "name": "John Doe"
      }'

5. 验证流程
1.在Step Functions控制台查看执行状态
2.在EventBridge控制台查看事件
3.在CloudWatch查看Lambda日志
资源清理
sam delete --stack-name user-registration-demo

流程说明
1.用户提交注册信息到API Gateway
2.RegistrationHandler Lambda启动Step Functions工作流
3.Step Functions执行:
- 验证用户数据(ValidateUserData)
- 创建用户账户(CreateUserAccount)
- 发送欢迎通知(SendWelcomeNotification)
- 发布注册完成事件(PublishRegistrationEvent)
4.EventBridge接收事件并路由到其他服务

## 业务场景流程说明

1. **API Gateway** 接收用户注册请求
2. **RegistrationHandler Lambda** 启动Step Functions工作流
3. **Step Functions** 依次执行：

   - ValidateUserData：验证邮箱格式和密码强度
   - CreateUserAccount：创建用户账户（模拟）
   - SendWelcomeNotification：发送欢迎通知（模拟）
   - PublishRegistrationEvent：发布注册完成事件
4. **EventBridge** 将事件路由到其他服务（如分析服务、通知服务等）


## 扩展可能性

1. 可以添加额外的Step Function步骤：

   - 发送短信验证码
   - 创建用户配置文件
   - 添加到邮件列表

2. 基于EventBridge事件可以触发：

   - 发送欢迎礼品
   - 更新用户统计信息
   - 触发营销活动

这个用户注册系统Demo展示了如何使用AWS核心服务构建一个完整的用户注册流程，具有清晰的步骤分离和事件驱动架构。

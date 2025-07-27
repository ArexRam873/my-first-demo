我将为您创建一个简单的订单处理系统demo，使用AWS的4个组件：API Gateway、Lambda、Step Function和EventBridge。
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>aws-demo</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <aws.lambda.java.version>1.2.3</aws.lambda.java.version>
        <aws.sdk.version>2.21.29</aws.sdk.version>
    </properties>

    <dependencies>
        <!-- AWS Lambda Core -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-lambda-java-core</artifactId>
            <version>${aws.lambda.java.version}</version>
        </dependency>

        <!-- AWS Lambda Events -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-lambda-java-events</artifactId>
            <version>3.11.3</version>
        </dependency>

        <!-- AWS SDK v2 -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>eventbridge</artifactId>
            <version>${aws.sdk.version}</version>
        </dependency>

        <!-- Jackson for JSON处理 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.15.2</version>
        </dependency>

        <!-- SLF4J for logging -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.9</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <createDependencyReducedPom>false</createDependencyReducedPom>
                </configuration>
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
```

```java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

import java.util.HashMap;
import java.util.Map;

public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final EventBridgeClient eventBridgeClient = EventBridgeClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        context.getLogger().log("Received API Gateway request: " + request.getBody());
        
        try {
            // 解析请求体
            Map<String, Object> requestBody = objectMapper.readValue(request.getBody(), Map.class);
            String orderId = (String) requestBody.get("orderId");
            
            context.getLogger().log("Processing order: " + orderId);
            
            // 发送事件到EventBridge
            sendEventToEventBridge(orderId, context);
            
            // 返回成功响应
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(200);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            response.setHeaders(headers);
            
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Order submitted successfully");
            responseBody.put("orderId", orderId);
            
            response.setBody(objectMapper.writeValueAsString(responseBody));
            return response;
            
        } catch (Exception e) {
            context.getLogger().log("Error processing request: " + e.getMessage());
            
            APIGatewayProxyResponseEvent errorResponse = new APIGatewayProxyResponseEvent();
            errorResponse.setStatusCode(500);
            errorResponse.setBody("{\"error\": \"Internal server error\"}");
            return errorResponse;
        }
    }
    
    private void sendEventToEventBridge(String orderId, Context context) {
        try {
            Map<String, Object> eventDetail = new HashMap<>();
            eventDetail.put("orderId", orderId);
            eventDetail.put("status", "submitted");
            
            PutEventsRequestEntry eventEntry = PutEventsRequestEntry.builder()
                    .source("order.api")
                    .detailType("Order Submitted")
                    .detail(objectMapper.writeValueAsString(eventDetail))
                    .build();
            
            PutEventsRequest eventsRequest = PutEventsRequest.builder()
                    .entries(eventEntry)
                    .build();
            
            eventBridgeClient.putEvents(eventsRequest);
            context.getLogger().log("Event sent to EventBridge for order: " + orderId);
            
        } catch (Exception e) {
            context.getLogger().log("Failed to send event to EventBridge: " + e.getMessage());
        }
    }
}
```

```java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class StepFunctionHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        context.getLogger().log("StepFunction received input: " + input.toString());
        
        String orderId = (String) input.get("orderId");
        String currentStatus = (String) input.get("status");
        
        context.getLogger().log("Processing order in StepFunction: " + orderId + ", status: " + currentStatus);
        
        // 模拟订单处理逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        
        if ("submitted".equals(currentStatus)) {
            result.put("status", "validated");
            result.put("message", "Order validated successfully");
            context.getLogger().log("Order validated: " + orderId);
        } else if ("validated".equals(currentStatus)) {
            result.put("status", "processed");
            result.put("message", "Order processed successfully");
            context.getLogger().log("Order processed: " + orderId);
        } else {
            result.put("status", "completed");
            result.put("message", "Order completed successfully");
            context.getLogger().log("Order completed: " + orderId);
        }
        
        return result;
    }
}
```

```java
package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudWatchLogsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class EventBridgeHandler implements RequestHandler<Map<String, Object>, String> {
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("EventBridge handler received event: " + event.toString());
        
        try {
            // 解析EventBridge事件
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            
            if (detail != null) {
                String orderId = (String) detail.get("orderId");
                String status = (String) detail.get("status");
                
                context.getLogger().log("Processing EventBridge event for order: " + orderId + ", status: " + status);
                
                // 模拟事件处理逻辑
                if ("submitted".equals(status)) {
                    context.getLogger().log("Order submission event processed for: " + orderId);
                    // 这里可以触发Step Function或其他处理
                } else {
                    context.getLogger().log("Other order event processed for: " + orderId);
                }
            }
            
            return "Event processed successfully";
            
        } catch (Exception e) {
            context.getLogger().log("Error processing EventBridge event: " + e.getMessage());
            throw new RuntimeException("Failed to process event", e);
        }
    }
}
```

```hcl
terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Variables
variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "aws-demo"
}

# Lambda执行角色
resource "aws_iam_role" "lambda_execution_role" {
  name = "${var.project_name}-lambda-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# Lambda基础权限
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
  role       = aws_iam_role.lambda_execution_role.name
}

# EventBridge权限
resource "aws_iam_role_policy" "lambda_eventbridge_policy" {
  name = "${var.project_name}-lambda-eventbridge-policy"
  role = aws_iam_role.lambda_execution_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "events:PutEvents"
        ]
        Resource = "*"
      }
    ]
  })
}

# API Gateway Lambda函数
resource "aws_lambda_function" "api_handler" {
  filename         = "target/aws-demo-1.0-SNAPSHOT.jar"
  function_name    = "${var.project_name}-api-handler"
  role            = aws_iam_role.lambda_execution_role.arn
  handler         = "com.example.lambda.ApiHandler::handleRequest"
  runtime         = "java21"
  timeout         = 30
  memory_size     = 512

  source_code_hash = filebase64sha256("target/aws-demo-1.0-SNAPSHOT.jar")

  environment {
    variables = {
      ENVIRONMENT = "dev"
    }
  }
}

# Step Function Lambda函数
resource "aws_lambda_function" "stepfunction_handler" {
  filename         = "target/aws-demo-1.0-SNAPSHOT.jar"
  function_name    = "${var.project_name}-stepfunction-handler"
  role            = aws_iam_role.lambda_execution_role.arn
  handler         = "com.example.lambda.StepFunctionHandler::handleRequest"
  runtime         = "java21"
  timeout         = 30
  memory_size     = 512

  source_code_hash = filebase64sha256("target/aws-demo-1.0-SNAPSHOT.jar")
}

# EventBridge Lambda函数
resource "aws_lambda_function" "eventbridge_handler" {
  filename         = "target/aws-demo-1.0-SNAPSHOT.jar"
  function_name    = "${var.project_name}-eventbridge-handler"
  role            = aws_iam_role.lambda_execution_role.arn
  handler         = "com.example.lambda.EventBridgeHandler::handleRequest"
  runtime         = "java21"
  timeout         = 30
  memory_size     = 512

  source_code_hash = filebase64sha256("target/aws-demo-1.0-SNAPSHOT.jar")
}

# API Gateway
resource "aws_api_gateway_rest_api" "main" {
  name        = "${var.project_name}-api"
  description = "API Gateway for ${var.project_name}"
}

resource "aws_api_gateway_resource" "orders" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_rest_api.main.root_resource_id
  path_part   = "orders"
}

resource "aws_api_gateway_method" "post_orders" {
  rest_api_id   = aws_api_gateway_rest_api.main.id
  resource_id   = aws_api_gateway_resource.orders.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "lambda_integration" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  resource_id = aws_api_gateway_resource.orders.id
  http_method = aws_api_gateway_method.post_orders.http_method

  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.api_handler.invoke_arn
}

resource "aws_api_gateway_deployment" "main" {
  depends_on = [
    aws_api_gateway_integration.lambda_integration,
  ]

  rest_api_id = aws_api_gateway_rest_api.main.id
  stage_name  = "dev"
}

# Lambda permission for API Gateway
resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.api_handler.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.main.execution_arn}/*/*"
}

# Step Functions状态机角色
resource "aws_iam_role" "stepfunction_role" {
  name = "${var.project_name}-stepfunction-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "states.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "stepfunction_lambda_policy" {
  name = "${var.project_name}-stepfunction-lambda-policy"
  role = aws_iam_role.stepfunction_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction"
        ]
        Resource = aws_lambda_function.stepfunction_handler.arn
      }
    ]
  })
}

# Step Functions状态机
resource "aws_sfn_state_machine" "order_processing" {
  name     = "${var.project_name}-order-processing"
  role_arn = aws_iam_role.stepfunction_role.arn

  definition = jsonencode({
    Comment = "Order processing workflow"
    StartAt = "ValidateOrder"
    States = {
      ValidateOrder = {
        Type = "Task"
        Resource = aws_lambda_function.stepfunction_handler.arn
        Next = "ProcessOrder"
      }
      ProcessOrder = {
        Type = "Task"
        Resource = aws_lambda_function.stepfunction_handler.arn
        Next = "CompleteOrder"
      }
      CompleteOrder = {
        Type = "Task"
        Resource = aws_lambda_function.stepfunction_handler.arn
        End = true
      }
    }
  })
}

# EventBridge自定义事件总线
resource "aws_cloudwatch_event_bus" "order_events" {
  name = "${var.project_name}-order-events"
}

# EventBridge规则
resource "aws_cloudwatch_event_rule" "order_submitted" {
  name           = "${var.project_name}-order-submitted"
  event_bus_name = aws_cloudwatch_event_bus.order_events.name

  event_pattern = jsonencode({
    source      = ["order.api"]
    detail-type = ["Order Submitted"]
  })
}

# EventBridge目标
resource "aws_cloudwatch_event_target" "lambda_target" {
  rule           = aws_cloudwatch_event_rule.order_submitted.name
  target_id      = "SendToLambda"
  arn            = aws_lambda_function.eventbridge_handler.arn
  event_bus_name = aws_cloudwatch_event_bus.order_events.name
}

# Lambda permission for EventBridge
resource "aws_lambda_permission" "eventbridge" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.eventbridge_handler.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.order_submitted.arn
}

# Outputs
output "api_gateway_url" {
  description = "API Gateway URL"
  value       = "${aws_api_gateway_deployment.main.invoke_url}/orders"
}

output "step_function_arn" {
  description = "Step Function ARN"
  value       = aws_sfn_state_machine.order_processing.arn
}

output "eventbridge_bus_name" {
  description = "EventBridge custom bus name"
  value       = aws_cloudwatch_event_bus.order_events.name
}
```

```text/markdown
# AWS四组件Demo部署指南

## 项目结构
```
aws-demo/
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── example/
│                   └── lambda/
│                       ├── ApiHandler.java
│                       ├── StepFunctionHandler.java
│                       └── EventBridgeHandler.java
├── main.tf
└── README.md
```

## 业务流程说明
1. **API Gateway** 接收订单请求
2. **Lambda (ApiHandler)** 处理请求并发送事件到 **EventBridge**
3. **EventBridge** 接收事件并触发 **Lambda (EventBridgeHandler)**
4. **Step Function** 执行订单处理工作流
5. 整个流程通过日志记录各个步骤

## 前置要求

### 1. 开发环境
- JDK 21
- Maven 3.8+
- IntelliJ IDEA
- AWS CLI
- Terraform

### 2. AWS账户配置
```bash
aws configure
# 输入您的 AWS Access Key ID、Secret Access Key、Region
```

## 部署步骤

### 步骤1: 项目初始化
```bash
# 创建项目目录
mkdir aws-demo
cd aws-demo

# 将提供的文件放置到对应位置
# 确保目录结构正确
```

### 步骤2: 编译Java项目
```bash
# 在项目根目录执行
mvn clean compile package

# 验证JAR文件生成
ls -la target/aws-demo-1.0-SNAPSHOT.jar
```

### 步骤3: Terraform部署
```bash
# 初始化Terraform
terraform init

# 查看执行计划
terraform plan

# 应用配置
terraform apply
# 输入 'yes' 确认部署
```

### 步骤4: 验证部署
部署完成后，Terraform会输出以下信息：
- `api_gateway_url`: API Gateway的调用URL
- `step_function_arn`: Step Function的ARN
- `eventbridge_bus_name`: EventBridge自定义总线名称

## 测试流程

### 1. 测试API Gateway
```bash
# 获取API Gateway URL（从terraform输出中获取）
API_URL="https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/dev/orders"

# 发送测试请求
curl -X POST $API_URL \
  -H "Content-Type: application/json" \
  -d '{"orderId": "12345", "product": "laptop"}'
```

### 2. 查看日志
```bash
# 查看API Handler日志
aws logs tail /aws/lambda/aws-demo-api-handler --follow

# 查看EventBridge Handler日志
aws logs tail /aws/lambda/aws-demo-eventbridge-handler --follow

# 查看Step Function Handler日志
aws logs tail /aws/lambda/aws-demo-stepfunction-handler --follow
```

### 3. 手动触发Step Function
```bash
# 获取Step Function ARN（从terraform输出中获取）
STEP_FUNCTION_ARN="arn:aws:states:us-east-1:xxxxxxxxxxxx:stateMachine:aws-demo-order-processing"

# 开始执行
aws stepfunctions start-execution \
  --state-machine-arn $STEP_FUNCTION_ARN \
  --input '{"orderId": "67890", "status": "submitted"}'
```

## 组件功能说明

### API Gateway
- 接收POST请求到 `/orders` 端点
- 调用ApiHandler Lambda函数
- 返回JSON响应

### Lambda函数
1. **ApiHandler**:
    - 处理API Gateway请求
    - 发送事件到EventBridge

2. **StepFunctionHandler**:
    - 处理Step Function的各个步骤
    - 模拟订单状态转换

3. **EventBridgeHandler**:
    - 处理EventBridge事件
    - 记录事件处理日志

### Step Function
- 定义三步工作流：ValidateOrder → ProcessOrder → CompleteOrder
- 每步都调用StepFunctionHandler Lambda

### EventBridge
- 自定义事件总线：`aws-demo-order-events`
- 事件规则匹配订单提交事件
- 触发EventBridgeHandler Lambda

## 清理资源
```bash
# 删除所有创建的AWS资源
terraform destroy
# 输入 'yes' 确认删除
```

## 常见问题

### Maven编译问题
```bash
# 如果遇到依赖问题，清理并重新下载
mvn clean
mvn dependency:resolve
mvn compile package
```

### Terraform权限问题
确保AWS用户具有以下权限：
- Lambda服务权限
- API Gateway权限
- Step Functions权限
- EventBridge权限
- IAM权限

### Lambda冷启动
Java 21 Lambda可能存在冷启动延迟，首次调用可能较慢，这是正常现象。

## 监控和调试
- 使用CloudWatch查看Lambda日志
- 使用Step Functions控制台查看工作流执行
- 使用EventBridge控制台查看事件流

## 成本优化
- Lambda按调用次数和执行时间计费
- API Gateway按请求数计费
- Step Functions按状态转换计费
- EventBridge按事件数计费

本demo的资源在AWS免费套餐范围内，正常测试不会产生费用。
```
我已经为您创建了一个完整的AWS四组件demo，包含以下特点：

## 项目亮点：
1. **简洁的业务场景**：订单处理系统，流程清晰
2. **代码量最少**：每个Lambda函数都很简洁，没有复杂POJO类
3. **四个组件都体现**：
   - **API Gateway**：接收HTTP请求
   - **Lambda**：三个处理函数（API处理、Step Function处理、EventBridge处理）
   - **Step Function**：三步订单处理工作流
   - **EventBridge**：事件驱动架构

## Maven版本控制：
- JDK 21
- AWS Lambda Java Core 1.2.3（稳定版本）
- AWS SDK v2 2.21.29（最新稳定版）
- Jackson 2.15.2（安全版本）
- Maven编译器插件3.11.0
- Maven Shade插件3.5.1

## 业务流程：
1. 用户发送POST请求到API Gateway `/orders`端点
2. ApiHandler接收请求，发送事件到EventBridge
3. EventBridge触发EventBridgeHandler处理事件
4. 可手动触发Step Function执行订单处理工作流
5. 整个过程通过日志记录，便于调试

## 部署特点：
- 完整的Terraform配置，一键部署
- 包含所有必要的IAM权限
- 输出重要的资源信息便于测试
- 支持完整的清理流程

这个demo代码量很少，但完整展示了四个AWS组件的集成使用，非常适合学习和演示用途。# my-first-demo
demo

这是一个 Terraform 模块，用于在 AWS 上搭建一个完整的无服务器（serverless）订单处理系统，集成了以下组件：

- **Lambda 函数**：用于处理 API 请求、Step Function 任务、EventBridge 事件。
- **API Gateway**：暴露 HTTP 接口。
- **EventBridge**：支持事件驱动架构。
- **Step Functions**：编排多个 Lambda 步骤任务。
- **IAM 权限配置**：为各服务赋予最小权限访问。

以下是对各部分的详细分析、关联关系、替换点说明：

---

## 🔧 Terraform 配置基础

```hcl
terraform {
  required_version = ">=1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~>5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}
```

### 🔍 解释：

- **`terraform` 块**：指定 Terraform 和 AWS Provider 的版本要求。
- **`provider "aws"`**：配置 AWS 区域，绑定变量 `var.aws_region`。

### ✅ 需要替换：
变量 `aws_region` 默认值为 `"us-east-1"`，可根据部署区域修改。

---

## 📦 Variables 定义

```hcl
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
```

### 🔍 解释：

- 用于自定义 AWS 区域和项目名，以简化命名和重复引用。

### ✅ 需要替换：
根据你的项目改写 `project_name`，如 `order-service`、`inventory-api` 等。

---

## 🛡 IAM 角色与权限（Lambda、StepFunction）

### Lambda 执行角色：

```hcl
resource "aws_iam_role" "lambda_execution_role" {
  name = "${var.project_name}-lambda-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}
```

### 附加基本权限：

```hcl
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
  role       = aws_iam_role.lambda_execution_role.name
}
```

### 允许向 EventBridge 写事件：

```hcl
resource "aws_iam_role_policy" "lambda_eventbridge_policy" {
  name = "${var.project_name}-lambda-eventbridge-policy"
  role = aws_iam_role.lambda_execution_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["events:PutEvents"]
      Resource = "*"
    }]
  })
}
```

### StepFunction 的角色与 Lambda 调用权限：

```hcl
resource "aws_iam_role" "stepfunction_role" {
  name = "${var.project_name}-stepfunction-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "states.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy" "stepfunction_lambda_policy" {
  name = "${var.project_name}-stepfunction-lambda-policy"
  role = aws_iam_role.stepfunction_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["lambda:InvokeFunction"]
      Resource = aws_lambda_function.stepfunction_handler.arn
    }]
  })
}
```

---

## ☁ Lambda 函数定义

### 公共参数说明：

```hcl
filename = "target/order-1.0-SNAPSHOT.jar"
runtime  = "java21"
timeout  = 30
memory_size = 512
```

### 三个 Lambda 函数：

#### 1. API Handler（处理 HTTP 请求）

```hcl
resource "aws_lambda_function" "api_handler" {
  function_name = "${var.project_name}-api-handler"
  handler       = "com.max.order.ApiHandler::handleRequest"
  ...
}
```

#### 2. Step Function Handler（被状态机调用）

```hcl
resource "aws_lambda_function" "stepfunction_handler" {
  function_name = "${var.project_name}-stepfunction-handler"
  handler       = "com.max.order.StepFunctionHandler::handleRequest"
  ...
}
```

#### 3. EventBridge Handler（响应事件）

```hcl
resource "aws_lambda_function" "eventbridge_handler" {
  function_name = "${var.project_name}-eventbridge-handler"
  handler       = "com.max.order.EventBridgeHandler::handleRequest"
  ...
}
```

### ✅ 需要替换：

- `handler`: 改为你项目 Java 主类的路径。
- `filename`: `.jar` 文件路径（可能在 CI/CD 构建中自动产出）。
- `function_name`: 根据实际业务命名更合理。

---

## 🌐 API Gateway 资源配置

### 创建 API：

```hcl
resource "aws_api_gateway_rest_api" "main" {
  name        = "${var.project_name}-api"
  description = "APIGateway for ${var.project_name}"
}
```

### 添加路径 `/orders`：

```hcl
resource "aws_api_gateway_resource" "orders" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_rest_api.main.root_resource_id
  path_part   = "orders"
}
```

### 配置 `POST /orders` 方法：

```hcl
resource "aws_api_gateway_method" "post_orders" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  resource_id = aws_api_gateway_resource.orders.id
  http_method = "POST"
  authorization = "NONE"
}
```

### 与 Lambda 集成：

```hcl
resource "aws_api_gateway_integration" "lambda_integration" {
  rest_api_id             = aws_api_gateway_rest_api.main.id
  resource_id             = aws_api_gateway_resource.orders.id
  http_method             = aws_api_gateway_method.post_orders.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.api_handler.invoke_arn
}
```

### 部署 API：

```hcl
resource "aws_api_gateway_deployment" "main" {
  depends_on = [aws_api_gateway_integration.lambda_integration]
  rest_api_id = aws_api_gateway_rest_api.main.id
  stage_name  = "dev"
}
```

### Lambda 权限授权 API Gateway 调用：

```hcl
resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.api_handler.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.main.execution_arn}/*/*"
}
```

---

## 🔄 Step Functions 状态机定义

```hcl
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
        Next     = "ProcessOrder"
      },
      ProcessOrder = {
        Type     = "Task"
        Resource = aws_lambda_function.stepfunction_handler.arn
        Next     = "CompleteOrder"
      },
      CompleteOrder = {
        Type     = "Task"
        Resource = aws_lambda_function.stepfunction_handler.arn
        End      = true
      }
    }
  })
}
```

### ✅ 说明：
每个状态都调用同一个 Lambda，但可以拆分为多个不同函数以提高可维护性。

---

## 📮 EventBridge 事件总线与规则

### 自定义总线：

```hcl
resource "aws_cloudwatch_event_bus" "order_events" {
  name = "${var.project_name}-order-events"
}
```

### 事件规则（捕捉事件）：

```hcl
resource "aws_cloudwatch_event_rule" "order_submitted" {
  name           = "${var.project_name}-order-submitted"
  event_bus_name = aws_cloudwatch_event_bus.order_events.name
  event_pattern = jsonencode({
    source      = ["order.api"]
    detail-type = ["OrderSubmitted"]
  })
}
```

### 目标 Lambda：

```hcl
resource "aws_cloudwatch_event_target" "lambda_target" {
  rule          = aws_cloudwatch_event_rule.order_submitted.name
  target_id     = "SendToLambda"
  arn           = aws_lambda_function.eventbridge_handler.arn
  event_bus_name = aws_cloudwatch_event_bus.order_events.name
}
```

### Lambda 权限授权 EventBridge 调用：

```hcl
resource "aws_lambda_permission" "eventbridge" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.eventbridge_handler.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.order_submitted.arn
}
```

---

## 📤 输出变量

```hcl
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

---

## ✅ 总结：哪些地方要替换？

| 部分 | 说明 | 替换 |
|------|------|------|
| `var.project_name` | 项目名前缀，统一命名资源 | ✅ |
| `handler` | Java 类路径，如 `com.xxx.ApiHandler::handleRequest` | ✅ |
| `.jar` 文件名 | 构建输出的路径 | ✅ |
| 区域 `aws_region` | 视你实际部署区域而定 | ✅ |

如果你想，我可以帮你把这份代码清洗成结构更清晰、模块化的模板。是否需要？
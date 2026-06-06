# C♭ Compiler Lab

> 一个使用 Java 17 + Spring Boot + Vue 3 + Monaco Editor 实现的教学型 C♭ 语言编译器实验平台。

![Java 17](https://img.shields.io/badge/Java-17-315d4c)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.6-6aa36f)
![Vue 3](https://img.shields.io/badge/Vue-3-42b883)
![Vite](https://img.shields.io/badge/Vite-6-646cff)
![Monaco Editor](https://img.shields.io/badge/Editor-Monaco-1f6feb)

## 功能概览

```text
C♭ 源代码
-> Lexer 生成 Token
-> Parser 生成 AST
-> SemanticAnalyzer 做语义检查
-> IRGenerator 生成三地址码
-> VirtualMachine 解释执行
-> 前端展示结果
```

### 已实现内容

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| Lexer | Done | 支持关键字、标识符、字面量、运算符、分隔符和注释。 |
| Parser / AST | Done | 手写递归下降 Parser，支持表达式优先级。 |
| SemanticAnalyzer | Done | 检查作用域、类型、函数调用、数组、循环控制和 `main`。 |
| IRGenerator | Done | 生成便于课程展示的三地址码文本。 |
| VirtualMachine | Done | 执行 C♭ 程序，支持变量、数组、函数、循环、`printf`。 |
| REST API | Done | 提供词法分析、语法分析、编译、运行四个接口。 |
| Frontend | Done | Vue 3 + Monaco Editor，可在线写代码、编译和运行。 |

## 快速开始

### 环境要求

- JDK 17 或更高版本
- Maven 3.9+
- Node.js 20+ 推荐
- npm 10+

> 如果本机同时安装了多个 JDK，请确保运行后端时 `JAVA_HOME` 指向 JDK 17 或更高版本。

### 1. 克隆项目

```bash
git clone https://github.com/hanabinano/cflat_compilor.git
cd cflat_compilor
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认运行在：

```text
http://localhost:8080
```

如果需要临时指定 JDK：

```bash
JAVA_HOME=$(/usr/libexec/java_home) mvn spring-boot:run
```

### 3. 启动前端

另开一个终端：

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在：

```text
http://localhost:5173
```

打开页面后，可以在左侧 Monaco 编辑器中输入 C♭ 代码，点击按钮查看 Token、AST、三地址码或运行结果。

## 页面使用说明

前端页面包含两个主要区域：

- 左侧：C♭ 代码编辑器。
- 右侧：标准输入、输出结果、错误信息。

工具栏包含四个按钮：

| 按钮 | 作用 |
| --- | --- |
| 词法分析 | 调用 `/api/lexer`，显示 Token 列表。 |
| 语法分析 | 调用 `/api/parser`，显示 AST。 |
| 编译 | 调用 `/api/compile`，显示三地址码。 |
| 编译并运行 | 调用 `/api/run`，显示 stdout、stderr、exitCode 和 IR。 |

页面内置了示例程序，可以从右上角下拉框切换。

## C♭ 语言说明

### 基本类型

| 类型 | 说明 |
| --- | --- |
| `int` | 整数 |
| `char` | 字符 |
| `bool` | 布尔值，取值为 `true` 或 `false` |
| `void` | 无返回值，只用于函数返回类型 |

### 支持的语法

- 变量声明：`int a;`、`int a = 3;`
- 一维数组：`int arr[10];`
- 数组元素访问：`arr[0] = 1;`
- 函数定义：`int add(int a, int b) { return a + b; }`
- 主函数：`int main() { ... }`
- 算术运算：`+`、`-`、`*`、`/`、`%`
- 关系运算：`<`、`<=`、`>`、`>=`、`==`、`!=`
- 逻辑运算：`&&`、`||`、`!`
- 控制流：`if` / `else`、`while`、`for`
- 循环控制：`break`、`continue`
- 返回语句：`return`
- 函数调用
- 简化输出：`printf(expr);`
- 注释：`//` 和 `/* */`

### 暂不支持

- 预处理器
- 指针
- 结构体
- 多文件编译
- 字符串字面量
- 复杂格式化输出
- `scanf`

## 示例程序

### 循环和数组

```c
int main() {
    int arr[5];
    int i = 0;
    int sum = 0;

    while (i < 5) {
        arr[i] = i + 1;
        sum = sum + arr[i];
        i = i + 1;
    }

    printf(sum);
    return 0;
}
```

输出：

```text
15
```

### 函数调用

```c
int add(int a, int b) {
    return a + b;
}

int main() {
    int result = add(2, 3);
    printf(result);
    return 0;
}
```

输出：

```text
5
```

### for 和 continue

```c
int main() {
    int i;
    int sum = 0;

    for (i = 0; i < 10; i = i + 1) {
        if (i == 5) {
            continue;
        }
        sum = sum + i;
    }

    printf(sum);
    return 0;
}
```

输出：

```text
40
```

## REST API

所有接口使用 JSON 请求体和 JSON 响应体。后端基础地址：

```text
http://localhost:8080/api
```

### POST `/api/lexer`

执行词法分析。

```bash
curl -X POST http://localhost:8080/api/lexer \
  -H "Content-Type: application/json" \
  -d '{"code":"int main(){ return 0; }"}'
```

返回：

```json
{
  "success": true,
  "tokens": [
    { "type": "KW_INT", "lexeme": "int", "line": 1, "column": 1 }
  ]
}
```

### POST `/api/parser`

执行词法分析和语法分析，返回 AST。

```bash
curl -X POST http://localhost:8080/api/parser \
  -H "Content-Type: application/json" \
  -d '{"code":"int main(){ return 0; }"}'
```

### POST `/api/compile`

执行词法分析、语法分析、语义分析和 IR 生成。

```bash
curl -X POST http://localhost:8080/api/compile \
  -H "Content-Type: application/json" \
  -d '{"code":"int main(){ printf(1 + 2); return 0; }"}'
```

返回：

```json
{
  "success": true,
  "ir": [
    "function main",
    "t0 = 1 + 2",
    "print",
    "return 0",
    "end function"
  ]
}
```

### POST `/api/run`

执行完整编译流程并运行程序。

```bash
curl -X POST http://localhost:8080/api/run \
  -H "Content-Type: application/json" \
  -d '{"code":"int main(){ printf(1 + 2); return 0; }","stdin":""}'
```

返回：

```json
{
  "success": true,
  "stdout": "3\n",
  "stderr": "",
  "exitCode": 0
}
```

### 错误响应

```json
{
  "success": false,
  "stage": "SEMANTIC",
  "message": "break/continue must be inside a loop.",
  "line": 0,
  "column": 0
}
```

`stage` 可能为：

- `LEXER`
- `PARSER`
- `SEMANTIC`
- `IR`
- `VM`

## 词法规则

### 关键字

```text
int char bool void
if else while for
break continue return
true false
printf
```

### 标识符

```ebnf
identifier = (letter | "_"), { letter | digit | "_" } ;
letter     = "A"..."Z" | "a"..."z" ;
digit      = "0"..."9" ;
```

### 字面量

```ebnf
int_literal  = digit, { digit } ;
char_literal = "'", char_body, "'" ;
bool_literal = "true" | "false" ;
```

### 运算符和分隔符

```text
+ - * / %
< <= > >= == !=
&& || !
=
( ) { } [ ] ; ,
```

## EBNF 语法概要

```ebnf
program              = { function_definition | global_declaration }, EOF ;
global_declaration   = type, declarator_list, ";" ;
function_definition  = type, identifier, "(", [ parameter_list ], ")", block ;
type                 = "int" | "char" | "bool" | "void" ;

block                = "{", { declaration | statement }, "}" ;
declaration          = type, declarator_list, ";" ;

statement            = block
                     | expression_statement
                     | if_statement
                     | while_statement
                     | for_statement
                     | break_statement
                     | continue_statement
                     | return_statement ;

expression           = assignment ;
assignment           = logical_or, [ "=", assignment ] ;
logical_or           = logical_and, { "||", logical_and } ;
logical_and          = equality, { "&&", equality } ;
equality             = relational, { ( "==" | "!=" ), relational } ;
relational           = additive, { ( "<" | "<=" | ">" | ">=" ), additive } ;
additive             = multiplicative, { ( "+" | "-" ), multiplicative } ;
multiplicative       = unary, { ( "*" | "/" | "%" ), unary } ;
unary                = ( "!" | "-" | "+" ), unary | postfix ;
postfix              = primary, { call_suffix | index_suffix } ;
primary              = identifier | int_literal | char_literal | bool_literal | "(", expression, ")" ;
```

## 项目结构

```text
cflat-compiler/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/example/cflat/
│       │   │   ├── compiler/
│       │   │   │   ├── lexer/
│       │   │   │   ├── parser/
│       │   │   │   ├── ast/
│       │   │   │   ├── semantic/
│       │   │   │   ├── ir/
│       │   │   │   └── vm/
│       │   │   ├── controller/
│       │   │   ├── dto/
│       │   │   └── service/
│       │   └── resources/
│       └── test/
├── frontend/
│   ├── package.json
│   ├── index.html
│   ├── vite.config.js
│   └── src/
│       ├── App.vue
│       ├── api/
│       └── components/
└── README.md
```

## 开发命令

### 后端

```bash
cd backend
mvn test
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
npm run build
```

## 测试情况

后端包含 JUnit 测试，覆盖：

- Lexer Token 生成。
- 函数调用编译为 IR。
- 循环和数组程序运行。
- 语义错误报告。

运行：

```bash
cd backend
mvn test
```

前端构建验证：

```bash
cd frontend
npm run build
```

## 说明

当前版本已经可以作为课程设计展示版使用。IR 以可读三地址码形式展示，VM 直接基于语义检查后的程序结构执行，以保证教学演示清晰稳定。后续可以继续把 VM 改造成严格解释 IR 指令的版本，并扩展 `scanf`、字符串字面量和更多标准库函数。

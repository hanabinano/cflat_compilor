# C♭ 编译器项目设计

C♭ 是一个教学用的类 C 语言。这里的 `♭` 是五线谱中的降号 flat，因此 C♭ 不是 C#。本项目目标是用 Java 手写一个完整的 C♭ 编译器核心，并配套一个可以写代码、编译、运行的 Web 前端页面。

第一阶段只完成总体设计和 README，不一次性实现完整项目。

## 1. C♭ 语言说明

C♭ 面向编译原理课程设计，语法风格接近 C，但刻意去掉会显著增加实现复杂度的部分。它不追求完全兼容 C 标准，而是重点展示完整编译器流程。

完整流程如下：

```text
C♭ 源代码
-> Lexer 词法分析，生成 Token
-> Parser 语法分析，生成 AST
-> SemanticAnalyzer 语义分析，检查类型和作用域
-> IRGenerator 生成三地址码
-> VirtualMachine 解释执行三地址码
-> 返回运行结果
```

### 设计目标

- 完整展示词法分析、语法分析、AST、语义分析、中间代码生成、虚拟机运行。
- 编译器核心完全使用 Java 17 手写。
- 后端使用 Spring Boot 提供 REST API。
- 前端使用 Vue 3 + Vite + Monaco Editor。
- 页面适合课程设计展示，可以直观看到 Token、AST、IR 和程序输出。

### 第一版不支持

- 预处理器：不支持 `#include`、`#define`。
- 指针。
- 结构体、联合体、枚举。
- 多文件编译。
- 完整 C 标准库。
- 复杂 `printf` 格式化字符串。
- 完整 `scanf`，后续版本可扩展。

### 基本类型

| 类型 | 说明 | 备注 |
| --- | --- | --- |
| `int` | 整数 | VM 中按 32 位有符号整数处理。 |
| `char` | 字符 | VM 中可按整数编码存储。 |
| `bool` | 布尔值 | 取值为 `true` 或 `false`。 |
| `void` | 无返回值 | 只用于函数返回类型。 |

### 第一版语言功能

- 变量声明：`int a;`、`int a = 3;`
- 一维数组：`int arr[10];`
- 数组元素访问和赋值：`arr[0] = 1;`
- 函数定义：`int add(int a, int b) { return a + b; }`
- 主函数：`int main() { ... }`
- 赋值语句：`a = 1 + 2;`
- 算术运算：`+`、`-`、`*`、`/`、`%`
- 关系运算：`<`、`<=`、`>`、`>=`、`==`、`!=`
- 逻辑运算：`&&`、`||`、`!`
- `if` / `else`
- `while`
- `for`
- `break` / `continue`
- `return`
- 函数调用
- 简化版输出：`printf(expr);`
- 单行注释：`// comment`
- 多行注释：`/* comment */`

## 2. 词法规则

词法分析器读取源代码，输出 Token 列表。每个 Token 至少包含：

- Token 类型
- 原始文本 `lexeme`
- 所在行号
- 所在列号

### 空白字符

以下空白字符用于分隔 Token，本身不产生 Token：

- 空格
- 制表符
- 回车
- 换行

### 注释

```c
// 单行注释

/* 多行注释
   可以跨越多行 */
```

注释不产生 Token，但词法分析器仍要维护正确的行号和列号，方便错误定位。

### 关键字

```text
int char bool void
if else while for
break continue return
true false
printf
```

`scanf` 暂不启用，可在后续版本中作为保留字加入。

### 标识符

标识符由字母或下划线开头，后面可以跟字母、数字或下划线。

```ebnf
identifier = (letter | "_"), { letter | digit | "_" } ;
letter     = "A"..."Z" | "a"..."z" ;
digit      = "0"..."9" ;
```

示例：

```c
a
sum
_temp
arr10
```

### 字面量

整数常量：

```ebnf
int_literal = digit, { digit } ;
```

字符常量：

```ebnf
char_literal = "'", char_body, "'" ;
char_body    = normal_char | escape_char ;
escape_char  = "\\n" | "\\t" | "\\r" | "\\0" | "\\'" | "\\\\" ;
```

布尔常量：

```text
true
false
```

第一版不支持字符串字面量。`printf` 只接收一个表达式。

### 运算符

| 类别 | 运算符 |
| --- | --- |
| 赋值 | `=` |
| 算术 | `+`、`-`、`*`、`/`、`%` |
| 关系 | `<`、`<=`、`>`、`>=`、`==`、`!=` |
| 逻辑 | `&&`、`||`、`!` |

### 分隔符

```text
( ) { } [ ] ; ,
```

### Token 返回示例

```json
{
  "type": "KW_INT",
  "lexeme": "int",
  "line": 1,
  "column": 1
}
```

Java 实现中建议使用更细粒度的 `TokenType` 枚举，例如：

- `KW_INT`
- `KW_RETURN`
- `IDENTIFIER`
- `INT_LITERAL`
- `CHAR_LITERAL`
- `BOOL_LITERAL`
- `PLUS`
- `LPAREN`
- `EOF`

## 3. EBNF 语法规则

Parser 使用手写递归下降实现。表达式部分建议使用分层递归下降或 Pratt Parser，以便清晰处理运算符优先级。

### 程序结构

```ebnf
program              = { function_definition | global_declaration }, EOF ;

global_declaration   = type, declarator_list, ";" ;
declarator_list      = declarator, { ",", declarator } ;
declarator           = identifier, [ array_suffix ], [ "=", expression ] ;
array_suffix         = "[", int_literal, "]" ;

function_definition  = type, identifier, "(", [ parameter_list ], ")", block ;
parameter_list       = parameter, { ",", parameter } ;
parameter            = type, identifier ;

type                 = "int" | "char" | "bool" | "void" ;
```

约束：

- 函数参数不能是 `void`。
- 第一版不支持数组参数。
- 变量类型不能是 `void`。
- 程序必须存在 `int main()`。

### 语句

```ebnf
block                = "{", { block_item }, "}" ;
block_item           = declaration | statement ;

declaration          = type, declarator_list, ";" ;

statement            = block
                     | expression_statement
                     | if_statement
                     | while_statement
                     | for_statement
                     | break_statement
                     | continue_statement
                     | return_statement ;

expression_statement = [ expression ], ";" ;

if_statement         = "if", "(", expression, ")", statement,
                       [ "else", statement ] ;

while_statement      = "while", "(", expression, ")", statement ;

for_statement        = "for", "(",
                       [ for_init ], ";",
                       [ expression ], ";",
                       [ expression ],
                       ")", statement ;

for_init             = declaration_without_semicolon | expression ;
declaration_without_semicolon
                     = type, declarator_list ;

break_statement      = "break", ";" ;
continue_statement   = "continue", ";" ;
return_statement     = "return", [ expression ], ";" ;
```

### 表达式

运算符优先级从高到低：

| 优先级 | 运算符 | 结合性 |
| --- | --- | --- |
| 1 | `()`、`[]`、函数调用 | 左结合 |
| 2 | 一元 `!`、一元 `-`、一元 `+` | 右结合 |
| 3 | `*`、`/`、`%` | 左结合 |
| 4 | `+`、`-` | 左结合 |
| 5 | `<`、`<=`、`>`、`>=` | 左结合 |
| 6 | `==`、`!=` | 左结合 |
| 7 | `&&` | 左结合 |
| 8 | `||` | 左结合 |
| 9 | `=` | 右结合 |

```ebnf
expression           = assignment ;

assignment           = logical_or, [ "=", assignment ] ;

logical_or           = logical_and, { "||", logical_and } ;
logical_and          = equality, { "&&", equality } ;
equality             = relational, { ( "==" | "!=" ), relational } ;
relational           = additive, { ( "<" | "<=" | ">" | ">=" ), additive } ;
additive             = multiplicative, { ( "+" | "-" ), multiplicative } ;
multiplicative       = unary, { ( "*" | "/" | "%" ), unary } ;
unary                = ( "!" | "-" | "+" ), unary
                     | postfix ;

postfix              = primary, { call_suffix | index_suffix } ;
call_suffix          = "(", [ argument_list ], ")" ;
index_suffix         = "[", expression, "]" ;
argument_list        = expression, { ",", expression } ;

primary              = identifier
                     | int_literal
                     | char_literal
                     | bool_literal
                     | "(", expression, ")" ;
```

赋值左值必须是：

```text
identifier
array_element
```

Parser 可以先按语法接受表达式结构，再由 SemanticAnalyzer 拒绝非法赋值，例如 `(a + b) = 3;`。

## 4. 编译器架构

### 后端技术栈

- Java 17
- Spring Boot
- 手写 Lexer
- 手写 Parser
- 手写 AST
- 手写 SemanticAnalyzer
- 手写 IRGenerator
- 手写 VirtualMachine
- 不使用 ANTLR、Yacc、Lex、LLVM

### 前端技术栈

- Vue 3
- Vite
- Monaco Editor
- REST API

### 后端模块

```text
backend/
├── compiler/
│   ├── lexer/
│   │   ├── Lexer
│   │   ├── Token
│   │   └── TokenType
│   ├── parser/
│   │   ├── Parser
│   │   └── ParseException
│   ├── ast/
│   │   ├── AstNode
│   │   ├── ProgramNode
│   │   ├── StatementNode
│   │   └── ExpressionNode
│   ├── semantic/
│   │   ├── SemanticAnalyzer
│   │   ├── Symbol
│   │   ├── SymbolTable
│   │   └── Type
│   ├── ir/
│   │   ├── IRGenerator
│   │   ├── Instruction
│   │   └── OpCode
│   └── vm/
│       ├── VirtualMachine
│       ├── RuntimeFrame
│       └── VMResult
├── controller/
│   └── CompilerController
└── dto/
    ├── CompileRequest
    ├── RunRequest
    ├── TokenResponse
    ├── AstResponse
    ├── CompileResponse
    └── RunResponse
```

### 各阶段职责

| 阶段 | 输入 | 输出 | 职责 |
| --- | --- | --- | --- |
| Lexer | 源代码 | Token 列表 | 识别关键字、标识符、字面量、运算符、分隔符、注释。 |
| Parser | Token 列表 | AST | 检查语法并构造抽象语法树。 |
| SemanticAnalyzer | AST | 带类型信息的 AST / 符号表 | 检查声明、作用域、类型、函数调用、循环控制。 |
| IRGenerator | 语义正确的 AST | 三地址码 | 把高级语法降低为标签、跳转、临时变量、调用、返回。 |
| VirtualMachine | IR + 标准输入 | 标准输出 + 退出码 | 执行指令，管理函数栈帧、变量、数组和运行时错误。 |

### 语义检查范围

- 同一作用域重复声明。
- 使用未声明的标识符。
- 不允许声明 `void` 类型变量。
- 数组长度必须是正整数常量。
- 数组下标必须是 `int`。
- 赋值目标必须是可赋值左值。
- 赋值左右类型必须兼容。
- `if`、`while`、`for` 条件表达式必须是 `bool`，或按项目规则允许 `int` 转换为条件。
- `break` 和 `continue` 必须出现在循环内部。
- 函数调用的参数数量和类型必须匹配。
- 非 `void` 函数必须返回值。
- `void` 函数不能返回值。
- 程序必须包含 `int main()`。

### 中间代码设计

IR 使用便于展示的三地址码文本形式。

示例：

```text
function main
t0 = 1 + 2
a = t0
param a
call printf, 1
return 0
end function
```

计划支持的指令类别：

- `LABEL name`
- `GOTO label`
- `IF_FALSE value GOTO label`
- `ASSIGN target value`
- `BINARY target op left right`
- `UNARY target op value`
- `ARRAY_LOAD target array index`
- `ARRAY_STORE array index value`
- `PARAM value`
- `CALL target function argc`
- `RETURN value`
- `PRINT value`

## 5. 前后端接口设计

所有接口使用 JSON 请求体和 JSON 响应体。

基础地址：

```text
http://localhost:8080/api
```

### 通用请求格式

```json
{
  "code": "int main() { printf(1 + 2); return 0; }"
}
```

### 通用错误格式

```json
{
  "success": false,
  "stage": "PARSER",
  "message": "Expected ';' after expression.",
  "line": 3,
  "column": 12
}
```

`stage` 可取：

- `LEXER`
- `PARSER`
- `SEMANTIC`
- `IR`
- `VM`

### POST `/api/lexer`

只执行词法分析，返回 Token 列表。

请求：

```json
{
  "code": "int main() { return 0; }"
}
```

响应：

```json
{
  "success": true,
  "tokens": [
    { "type": "KW_INT", "lexeme": "int", "line": 1, "column": 1 },
    { "type": "IDENTIFIER", "lexeme": "main", "line": 1, "column": 5 },
    { "type": "LPAREN", "lexeme": "(", "line": 1, "column": 9 }
  ]
}
```

### POST `/api/parser`

执行词法分析和语法分析，返回 AST。

请求：

```json
{
  "code": "int main() { return 0; }"
}
```

响应：

```json
{
  "success": true,
  "ast": {
    "kind": "Program",
    "functions": [
      {
        "kind": "FunctionDefinition",
        "name": "main",
        "returnType": "int",
        "parameters": [],
        "body": {
          "kind": "BlockStatement"
        }
      }
    ]
  }
}
```

### POST `/api/compile`

执行词法分析、语法分析、语义分析和 IR 生成，返回三地址码。

请求：

```json
{
  "code": "int main() { printf(1 + 2); return 0; }"
}
```

响应：

```json
{
  "success": true,
  "ir": [
    "function main",
    "t0 = 1 + 2",
    "print t0",
    "return 0",
    "end function"
  ]
}
```

### POST `/api/run`

执行完整编译流程，并用虚拟机运行生成的 IR。

请求：

```json
{
  "code": "int main() { printf(1 + 2); return 0; }",
  "stdin": ""
}
```

响应：

```json
{
  "success": true,
  "ir": [
    "function main",
    "t0 = 1 + 2",
    "print t0",
    "return 0",
    "end function"
  ],
  "stdout": "3\n",
  "stderr": "",
  "exitCode": 0
}
```

## 6. 项目目录结构

第一阶段建立的目录结构：

```text
cflat-compiler/
├── backend/
│   ├── compiler/
│   │   ├── lexer/
│   │   ├── parser/
│   │   ├── ast/
│   │   ├── semantic/
│   │   ├── ir/
│   │   └── vm/
│   ├── controller/
│   └── dto/
├── frontend/
└── README.md
```

后续 Spring Boot 项目可以扩展为：

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/example/cflat/
    │   │       ├── CflatCompilerApplication.java
    │   │       ├── compiler/
    │   │       ├── controller/
    │   │       └── dto/
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/
```

后续 Vue 前端可以扩展为：

```text
frontend/
├── package.json
├── index.html
├── vite.config.js
└── src/
    ├── main.js
    ├── App.vue
    ├── api/
    │   └── compilerApi.js
    └── components/
        ├── CodeEditor.vue
        ├── ActionToolbar.vue
        └── OutputPanel.vue
```

## 7. 示例程序

### 输出数字

```c
int main() {
    printf(123);
    return 0;
}
```

期望输出：

```text
123
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

期望输出：

```text
5
```

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

期望输出：

```text
15
```

### for 循环和 continue

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

期望输出：

```text
40
```

### break

```c
int main() {
    int i = 0;

    while (true) {
        if (i == 3) {
            break;
        }
        i = i + 1;
    }

    printf(i);
    return 0;
}
```

期望输出：

```text
3
```

## 8. 开发计划

### 第一阶段：项目设计和 README

- 明确语言范围。
- 明确词法规则。
- 明确 EBNF 语法。
- 明确编译器架构。
- 明确前后端接口。
- 建立项目目录。
- 提供示例程序。

### 第二阶段：后端 Spring Boot 骨架

- 创建 Java 17 + Spring Boot 项目。
- 添加 controller 和 dto。
- 添加统一错误响应模型。
- 建立单元测试目录。
- 验证基础接口可访问。

### 第三阶段：Lexer

- 实现 `TokenType`、`Token`、`Lexer`。
- 支持关键字、标识符、字面量、运算符、分隔符、空白、注释。
- 实现准确的行列号。
- 编写词法分析测试。
- 接入 `/api/lexer`。

### 第四阶段：Parser 和 AST

- 定义 AST 节点层次。
- 实现递归下降 Parser。
- 实现表达式优先级分析。
- 实现语法错误报告。
- 编写语法分析测试。
- 接入 `/api/parser`。

### 第五阶段：SemanticAnalyzer

- 实现类型系统。
- 实现嵌套符号表。
- 检查声明、赋值、函数调用、循环控制、返回值和 `main`。
- 编写语义分析测试。

### 第六阶段：IRGenerator

- 定义三地址码指令。
- 生成临时变量和标签。
- 降低条件、循环、函数调用、返回、数组、`printf`。
- 接入 `/api/compile`。

### 第七阶段：VirtualMachine

- 实现运行时值。
- 实现函数调用栈帧。
- 实现全局变量、局部变量和数组存储。
- 执行 IR 指令。
- 处理运行时错误。
- 接入 `/api/run`。

### 第八阶段：Frontend

- 创建 Vue 3 + Vite 项目。
- 集成 Monaco Editor。
- 实现左侧代码编辑器和右侧输出区域。
- 添加四个按钮：词法分析、语法分析、编译、编译并运行。
- 展示 Token、AST、IR、程序输出和错误信息。
- 做简洁、适合课程设计展示的响应式页面。

### 第九阶段：集成和文档完善

- 加入内置示例程序。
- 补充运行说明。
- 补充页面截图。
- 整理课程设计报告要点。
- 优化错误提示和输出展示。

## 实现约定

- 编译器核心不依赖 Spring Boot，便于单独测试。
- Controller 只负责接收请求、调用编译器服务、返回响应。
- AST 和 IR 要便于序列化，方便前端展示。
- 错误信息尽量包含阶段、行号、列号和简洁说明。
- 前端首屏直接展示代码编辑器、操作按钮和输出区域，不做营销式首页。
- 文档和 UI 可以显示 `C♭`，Java 包名、类名、文件名使用 `cflat`。

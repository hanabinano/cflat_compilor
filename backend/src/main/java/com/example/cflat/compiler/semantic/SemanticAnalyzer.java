package com.example.cflat.compiler.semantic;

import com.example.cflat.compiler.CompilerException;
import com.example.cflat.compiler.Stage;
import com.example.cflat.compiler.ast.Ast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticAnalyzer {
    private final Map<String, Ast.FunctionDef> functions = new HashMap<>();
    private Scope scope;
    private Ast.Type currentReturnType;
    private int loopDepth;

    public Ast.Program analyze(Ast.Program program) {
        scope = new Scope(null);
        for (Ast.VarDecl global : program.globals()) {
            declareVarDecl(global);
        }
        for (Ast.FunctionDef function : program.functions()) {
            if (functions.put(function.name(), function) != null) {
                fail("Duplicate function: " + function.name());
            }
        }
        Ast.FunctionDef main = functions.get("main");
        if (main == null || main.returnType() != Ast.Type.INT || !main.parameters().isEmpty()) {
            fail("Program must contain int main().");
        }
        for (Ast.FunctionDef function : program.functions()) {
            analyzeFunction(function);
        }
        return program;
    }

    private void analyzeFunction(Ast.FunctionDef function) {
        currentReturnType = function.returnType();
        Scope previous = scope;
        scope = new Scope(scope);
        for (Ast.Parameter parameter : function.parameters()) {
            if (parameter.type() == Ast.Type.VOID) {
                fail("Function parameter cannot be void: " + parameter.name());
            }
            scope.declare(parameter.name(), new Symbol(parameter.type(), false, 0));
        }
        analyzeStatement(function.body());
        scope = previous;
    }

    private void analyzeStatement(Ast.Statement statement) {
        if (statement == null) {
            return;
        }
        if (statement instanceof Ast.Block block) {
            Scope previous = scope;
            scope = new Scope(scope);
            for (Ast.Statement item : block.statements()) {
                analyzeStatement(item);
            }
            scope = previous;
        } else if (statement instanceof Ast.VarDecl varDecl) {
            declareVarDecl(varDecl);
        } else if (statement instanceof Ast.ExprStmt exprStmt) {
            if (exprStmt.expression() != null) {
                expressionType(exprStmt.expression());
            }
        } else if (statement instanceof Ast.IfStmt ifStmt) {
            requireCondition(ifStmt.condition());
            analyzeStatement(ifStmt.thenBranch());
            analyzeStatement(ifStmt.elseBranch());
        } else if (statement instanceof Ast.WhileStmt whileStmt) {
            requireCondition(whileStmt.condition());
            loopDepth++;
            analyzeStatement(whileStmt.body());
            loopDepth--;
        } else if (statement instanceof Ast.ForStmt forStmt) {
            Scope previous = scope;
            scope = new Scope(scope);
            if (forStmt.init() != null) {
                analyzeStatement(forStmt.init());
            }
            if (forStmt.condition() != null) {
                requireCondition(forStmt.condition());
            }
            if (forStmt.update() != null) {
                expressionType(forStmt.update());
            }
            loopDepth++;
            analyzeStatement(forStmt.body());
            loopDepth--;
            scope = previous;
        } else if (statement instanceof Ast.BreakStmt || statement instanceof Ast.ContinueStmt) {
            if (loopDepth == 0) {
                fail("break/continue must be inside a loop.");
            }
        } else if (statement instanceof Ast.ReturnStmt returnStmt) {
            if (currentReturnType == Ast.Type.VOID) {
                if (returnStmt.value() != null) {
                    fail("Void function cannot return a value.");
                }
            } else {
                if (returnStmt.value() == null) {
                    fail("Non-void function must return a value.");
                }
                requireAssignable(currentReturnType, expressionType(returnStmt.value()));
            }
        }
    }

    private void declareVarDecl(Ast.VarDecl varDecl) {
        if (varDecl.type() == Ast.Type.VOID) {
            fail("Variable type cannot be void.");
        }
        for (Ast.Declarator declarator : varDecl.declarators()) {
            if (declarator.arraySize() != null && declarator.arraySize() <= 0) {
                fail("Array size must be positive: " + declarator.name());
            }
            if (declarator.initializer() != null && declarator.arraySize() != null) {
                fail("Array initializer is not supported in v1: " + declarator.name());
            }
            if (declarator.initializer() != null) {
                requireAssignable(varDecl.type(), expressionType(declarator.initializer()));
            }
            scope.declare(declarator.name(), new Symbol(varDecl.type(), declarator.arraySize() != null,
                    declarator.arraySize() == null ? 0 : declarator.arraySize()));
        }
    }

    private Ast.Type expressionType(Ast.Expression expression) {
        if (expression instanceof Ast.Literal literal) {
            return literal.type();
        }
        if (expression instanceof Ast.StringLiteral) {
            return Ast.Type.STRING;
        }
        if (expression instanceof Ast.Variable variable) {
            Symbol symbol = scope.resolve(variable.name());
            if (symbol == null) {
                fail("Undefined variable: " + variable.name());
            }
            if (symbol.array()) {
                fail("Array variable requires an index: " + variable.name());
            }
            return symbol.type();
        }
        if (expression instanceof Ast.ArrayAccess access) {
            Symbol symbol = scope.resolve(access.array());
            if (symbol == null) {
                fail("Undefined array: " + access.array());
            }
            if (!symbol.array()) {
                fail("Variable is not an array: " + access.array());
            }
            requireAssignable(Ast.Type.INT, expressionType(access.index()));
            return symbol.type();
        }
        if (expression instanceof Ast.Unary unary) {
            Ast.Type type = expressionType(unary.expression());
            if ("!".equals(unary.operator())) {
                requireCondition(unary.expression());
                return Ast.Type.BOOL;
            }
            requireNumeric(type);
            return Ast.Type.INT;
        }
        if (expression instanceof Ast.Binary binary) {
            Ast.Type left = expressionType(binary.left());
            Ast.Type right = expressionType(binary.right());
            String op = binary.operator();
            if (List.of("+", "-", "*", "/", "%").contains(op)) {
                requireNumeric(left);
                requireNumeric(right);
                return Ast.Type.INT;
            }
            if (List.of("<", "<=", ">", ">=").contains(op)) {
                requireNumeric(left);
                requireNumeric(right);
                return Ast.Type.BOOL;
            }
            if (List.of("==", "!=").contains(op)) {
                requireAssignable(left, right);
                return Ast.Type.BOOL;
            }
            requireCondition(binary.left());
            requireCondition(binary.right());
            return Ast.Type.BOOL;
        }
        if (expression instanceof Ast.Assign assign) {
            Ast.Type target = assignTargetType(assign.target());
            Ast.Type value = expressionType(assign.value());
            requireAssignable(target, value);
            return target;
        }
        if (expression instanceof Ast.Call call) {
            if ("printf".equals(call.callee())) {
                checkPrintf(call);
                return Ast.Type.VOID;
            }
            if ("scanf".equals(call.callee())) {
                checkScanf(call);
                return Ast.Type.VOID;
            }
            Ast.FunctionDef function = functions.get(call.callee());
            if (function == null) {
                fail("Undefined function: " + call.callee());
            }
            if (function.parameters().size() != call.arguments().size()) {
                fail("Argument count mismatch when calling " + call.callee());
            }
            for (int i = 0; i < function.parameters().size(); i++) {
                requireAssignable(function.parameters().get(i).type(), expressionType(call.arguments().get(i)));
            }
            return function.returnType();
        }
        fail("Unsupported expression.");
        return Ast.Type.VOID;
    }

    private Ast.Type assignTargetType(Ast.Expression target) {
        if (target instanceof Ast.Variable variable) {
            Symbol symbol = scope.resolve(variable.name());
            if (symbol == null) {
                fail("Undefined variable: " + variable.name());
            }
            if (symbol.array()) {
                fail("Cannot assign to whole array: " + variable.name());
            }
            return symbol.type();
        }
        if (target instanceof Ast.ArrayAccess access) {
            return expressionType(access);
        }
        fail("Invalid assignment target.");
        return Ast.Type.VOID;
    }

    private void requireCondition(Ast.Expression expression) {
        Ast.Type type = expressionType(expression);
        if (type != Ast.Type.BOOL && type != Ast.Type.INT && type != Ast.Type.CHAR) {
            fail("Condition must be bool or numeric.");
        }
    }

    private void requireNumeric(Ast.Type type) {
        if (type != Ast.Type.INT && type != Ast.Type.CHAR) {
            fail("Expected numeric expression.");
        }
    }

    private void requireAssignable(Ast.Type target, Ast.Type value) {
        if (target == value) {
            return;
        }
        if ((target == Ast.Type.INT || target == Ast.Type.CHAR || target == Ast.Type.BOOL)
                && (value == Ast.Type.INT || value == Ast.Type.CHAR || value == Ast.Type.BOOL)) {
            return;
        }
        fail("Type mismatch: cannot assign " + value + " to " + target + ".");
    }

    private void checkPrintf(Ast.Call call) {
        if (call.arguments().isEmpty()) {
            fail("printf expects at least one argument.");
        }
        Ast.Expression first = call.arguments().get(0);
        if (first instanceof Ast.StringLiteral format) {
            int specifiers = countFormatSpecifiers(format.value());
            int provided = call.arguments().size() - 1;
            if (specifiers != provided) {
                fail("printf format expects " + specifiers + " argument(s) but got " + provided + ".");
            }
            for (int i = 1; i < call.arguments().size(); i++) {
                Ast.Type type = expressionType(call.arguments().get(i));
                if (type == Ast.Type.VOID || type == Ast.Type.STRING) {
                    fail("printf argument " + i + " must be a value.");
                }
            }
            return;
        }
        if (call.arguments().size() != 1) {
            fail("printf without a format string expects exactly one argument.");
        }
        Ast.Type type = expressionType(first);
        if (type == Ast.Type.VOID) {
            fail("printf cannot print a void value.");
        }
    }

    private int countFormatSpecifiers(String format) {
        int count = 0;
        for (int i = 0; i < format.length(); i++) {
            if (format.charAt(i) == '%' && i + 1 < format.length()) {
                char spec = format.charAt(++i);
                if (spec == '%') {
                    continue;
                }
                if (spec != 'd' && spec != 'c' && spec != 's') {
                    fail("Unsupported printf format specifier: %" + spec);
                }
                count++;
            }
        }
        return count;
    }

    private void checkScanf(Ast.Call call) {
        if (call.arguments().isEmpty()) {
            fail("scanf expects at least one target.");
        }
        for (Ast.Expression argument : call.arguments()) {
            Ast.Type type;
            if (argument instanceof Ast.Variable variable) {
                Symbol symbol = scope.resolve(variable.name());
                if (symbol == null) {
                    fail("Undefined variable: " + variable.name());
                }
                if (symbol.array()) {
                    fail("scanf target requires an index: " + variable.name());
                }
                type = symbol.type();
            } else if (argument instanceof Ast.ArrayAccess access) {
                type = expressionType(access);
            } else {
                fail("scanf target must be a variable or array element.");
                return;
            }
            if (type != Ast.Type.INT && type != Ast.Type.CHAR && type != Ast.Type.BOOL) {
                fail("scanf can only read numeric or boolean targets.");
            }
        }
    }

    private void fail(String message) {
        throw new CompilerException(Stage.SEMANTIC, message, 0, 0);
    }

    private record Symbol(Ast.Type type, boolean array, int size) {
    }

    private static class Scope {
        private final Scope parent;
        private final Map<String, Symbol> symbols = new HashMap<>();

        Scope(Scope parent) {
            this.parent = parent;
        }

        void declare(String name, Symbol symbol) {
            if (symbols.containsKey(name)) {
                throw new CompilerException(Stage.SEMANTIC, "Duplicate declaration: " + name, 0, 0);
            }
            symbols.put(name, symbol);
        }

        Symbol resolve(String name) {
            Symbol symbol = symbols.get(name);
            if (symbol != null) {
                return symbol;
            }
            return parent == null ? null : parent.resolve(name);
        }
    }
}

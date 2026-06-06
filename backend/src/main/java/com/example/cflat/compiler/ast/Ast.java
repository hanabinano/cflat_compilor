package com.example.cflat.compiler.ast;

import java.util.List;

public final class Ast {
    private Ast() {
    }

    public enum Type {
        INT, CHAR, BOOL, VOID, STRING
    }

    public interface Node {
    }

    public interface Statement extends Node {
    }

    public interface Expression extends Node {
    }

    public record Program(List<FunctionDef> functions, List<VarDecl> globals) implements Node {
    }

    public record Parameter(Type type, String name) implements Node {
    }

    public record FunctionDef(Type returnType, String name, List<Parameter> parameters, Block body) implements Node {
    }

    public record Declarator(String name, Integer arraySize, Expression initializer) implements Node {
    }

    public record VarDecl(Type type, List<Declarator> declarators) implements Statement {
    }

    public record Block(List<Statement> statements) implements Statement {
    }

    public record ExprStmt(Expression expression) implements Statement {
    }

    public record IfStmt(Expression condition, Statement thenBranch, Statement elseBranch) implements Statement {
    }

    public record WhileStmt(Expression condition, Statement body) implements Statement {
    }

    public record ForStmt(Statement init, Expression condition, Expression update, Statement body) implements Statement {
    }

    public record BreakStmt() implements Statement {
    }

    public record ContinueStmt() implements Statement {
    }

    public record ReturnStmt(Expression value) implements Statement {
    }

    public record Literal(Object value, Type type) implements Expression {
    }

    public record StringLiteral(String value) implements Expression {
    }

    public record Variable(String name) implements Expression {
    }

    public record ArrayAccess(String array, Expression index) implements Expression {
    }

    public record Unary(String operator, Expression expression) implements Expression {
    }

    public record Binary(Expression left, String operator, Expression right) implements Expression {
    }

    public record Assign(Expression target, Expression value) implements Expression {
    }

    public record Call(String callee, List<Expression> arguments) implements Expression {
    }
}

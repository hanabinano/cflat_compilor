package com.example.cflat.compiler.ir;

import com.example.cflat.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

public class IRGenerator {
    private final List<String> lines = new ArrayList<>();
    private final ArrayDeque<LoopContext> loops = new ArrayDeque<>();
    private int tempIndex;
    private int labelIndex;

    public IRProgram generate(Ast.Program program) {
        lines.clear();
        loops.clear();
        tempIndex = 0;
        labelIndex = 0;
        for (Ast.VarDecl global : program.globals()) {
            emitDeclaration(global);
        }
        for (Ast.FunctionDef function : program.functions()) {
            lines.add("function " + function.name());
            for (Ast.Parameter parameter : function.parameters()) {
                lines.add("param_def " + parameter.name());
            }
            emitStatement(function.body());
            lines.add("end function");
        }
        return new IRProgram(program, List.copyOf(lines));
    }

    private void emitDeclaration(Ast.VarDecl varDecl) {
        for (Ast.Declarator declarator : varDecl.declarators()) {
            if (declarator.arraySize() == null) {
                lines.add("declare " + varDecl.type().name().toLowerCase() + " " + declarator.name());
                if (declarator.initializer() != null) {
                    lines.add(declarator.name() + " = " + emitExpression(declarator.initializer()));
                }
            } else {
                lines.add("declare " + varDecl.type().name().toLowerCase() + " " + declarator.name() + "[" + declarator.arraySize() + "]");
            }
        }
    }

    private void emitStatement(Ast.Statement statement) {
        if (statement == null) {
            return;
        }
        if (statement instanceof Ast.Block block) {
            for (Ast.Statement item : block.statements()) {
                emitStatement(item);
            }
        } else if (statement instanceof Ast.VarDecl varDecl) {
            emitDeclaration(varDecl);
        } else if (statement instanceof Ast.ExprStmt exprStmt) {
            if (exprStmt.expression() != null) {
                emitExpression(exprStmt.expression());
            }
        } else if (statement instanceof Ast.IfStmt ifStmt) {
            String elseLabel = label("else");
            String endLabel = label("endif");
            String condition = emitExpression(ifStmt.condition());
            lines.add("if_false " + condition + " goto " + elseLabel);
            emitStatement(ifStmt.thenBranch());
            lines.add("goto " + endLabel);
            lines.add(elseLabel + ":");
            emitStatement(ifStmt.elseBranch());
            lines.add(endLabel + ":");
        } else if (statement instanceof Ast.WhileStmt whileStmt) {
            String start = label("while_start");
            String end = label("while_end");
            loops.push(new LoopContext(start, end));
            lines.add(start + ":");
            String condition = emitExpression(whileStmt.condition());
            lines.add("if_false " + condition + " goto " + end);
            emitStatement(whileStmt.body());
            lines.add("goto " + start);
            lines.add(end + ":");
            loops.pop();
        } else if (statement instanceof Ast.ForStmt forStmt) {
            String start = label("for_start");
            String update = label("for_update");
            String end = label("for_end");
            emitStatement(forStmt.init());
            loops.push(new LoopContext(update, end));
            lines.add(start + ":");
            if (forStmt.condition() != null) {
                lines.add("if_false " + emitExpression(forStmt.condition()) + " goto " + end);
            }
            emitStatement(forStmt.body());
            lines.add(update + ":");
            if (forStmt.update() != null) {
                emitExpression(forStmt.update());
            }
            lines.add("goto " + start);
            lines.add(end + ":");
            loops.pop();
        } else if (statement instanceof Ast.BreakStmt) {
            lines.add("goto " + loops.peek().breakLabel());
        } else if (statement instanceof Ast.ContinueStmt) {
            lines.add("goto " + loops.peek().continueLabel());
        } else if (statement instanceof Ast.ReturnStmt returnStmt) {
            lines.add(returnStmt.value() == null ? "return" : "return " + emitExpression(returnStmt.value()));
        }
    }

    private String emitExpression(Ast.Expression expression) {
        if (expression instanceof Ast.Literal literal) {
            Object value = literal.value();
            if (literal.type() == Ast.Type.BOOL) {
                return Boolean.TRUE.equals(value) ? "true" : "false";
            }
            return String.valueOf(value);
        }
        if (expression instanceof Ast.StringLiteral stringLiteral) {
            return encodeString(stringLiteral.value());
        }
        if (expression instanceof Ast.Variable variable) {
            return variable.name();
        }
        if (expression instanceof Ast.ArrayAccess access) {
            String temp = temp();
            lines.add(temp + " = " + access.array() + "[" + emitExpression(access.index()) + "]");
            return temp;
        }
        if (expression instanceof Ast.Unary unary) {
            String temp = temp();
            lines.add(temp + " = " + unary.operator() + emitExpression(unary.expression()));
            return temp;
        }
        if (expression instanceof Ast.Binary binary) {
            String left = emitExpression(binary.left());
            String right = emitExpression(binary.right());
            String temp = temp();
            lines.add(temp + " = " + left + " " + binary.operator() + " " + right);
            return temp;
        }
        if (expression instanceof Ast.Assign assign) {
            String value = emitExpression(assign.value());
            if (assign.target() instanceof Ast.Variable variable) {
                lines.add(variable.name() + " = " + value);
                return variable.name();
            }
            Ast.ArrayAccess access = (Ast.ArrayAccess) assign.target();
            lines.add(access.array() + "[" + emitExpression(access.index()) + "] = " + value);
            return value;
        }
        if (expression instanceof Ast.Call call) {
            if ("scanf".equals(call.callee())) {
                for (Ast.Expression target : call.arguments()) {
                    lines.add("read " + emitLValue(target));
                }
                return "void";
            }
            if ("printf".equals(call.callee())) {
                boolean formatMode = !call.arguments().isEmpty()
                        && call.arguments().get(0) instanceof Ast.StringLiteral;
                if (!formatMode && call.arguments().size() == 1) {
                    lines.add("param " + emitExpression(call.arguments().get(0)));
                    lines.add("print");
                    return "void";
                }
                for (Ast.Expression argument : call.arguments()) {
                    lines.add("param " + emitExpression(argument));
                }
                lines.add("printf " + call.arguments().size());
                return "void";
            }
            for (Ast.Expression argument : call.arguments()) {
                lines.add("param " + emitExpression(argument));
            }
            String temp = temp();
            lines.add(temp + " = call " + call.callee() + ", " + call.arguments().size());
            return temp;
        }
        return "<?>";
    }

    private String emitLValue(Ast.Expression target) {
        if (target instanceof Ast.Variable variable) {
            return variable.name();
        }
        if (target instanceof Ast.ArrayAccess access) {
            return access.array() + "[" + emitExpression(access.index()) + "]";
        }
        return "<?>";
    }

    private String encodeString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                case '\0' -> out.append("\\0");
                default -> out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }

    private String temp() {
        return "t" + tempIndex++;
    }

    private String label(String prefix) {
        return "L_" + prefix + "_" + labelIndex++;
    }

    private record LoopContext(String continueLabel, String breakLabel) {
    }
}

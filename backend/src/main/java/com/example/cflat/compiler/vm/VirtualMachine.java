package com.example.cflat.compiler.vm;

import com.example.cflat.compiler.CompilerException;
import com.example.cflat.compiler.Stage;
import com.example.cflat.compiler.ast.Ast;
import com.example.cflat.compiler.ir.IRProgram;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VirtualMachine {
    private final Map<String, FunctionBlock> functions = new HashMap<>();
    private final Map<String, Cell> globals = new HashMap<>();
    private final StringBuilder stdout = new StringBuilder();

    public VMResult execute(IRProgram program, String stdin) {
        functions.clear();
        globals.clear();
        stdout.setLength(0);
        loadProgram(program.instructions());
        RuntimeValue exit = call("main", List.of());
        return new VMResult(stdout.toString(), "", exit.asInt());
    }

    private void loadProgram(List<String> instructions) {
        FunctionBlock current = null;
        for (String rawLine : instructions) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("function ")) {
                String name = line.substring("function ".length()).trim();
                current = new FunctionBlock(name);
                functions.put(name, current);
                continue;
            }
            if ("end function".equals(line)) {
                current = null;
                continue;
            }
            if (current == null) {
                executeGlobal(line);
            } else {
                current.add(line);
            }
        }
    }

    private void executeGlobal(String line) {
        if (line.startsWith("declare ")) {
            declare(globals, line);
            return;
        }
        fail("Only declarations are allowed at global IR level: " + line);
    }

    private RuntimeValue call(String name, List<RuntimeValue> args) {
        FunctionBlock function = functions.get(name);
        if (function == null) {
            fail("Undefined function: " + name);
        }
        Frame frame = new Frame(globals);
        if (function.params.size() != args.size()) {
            fail("Argument count mismatch when calling " + name);
        }
        for (int i = 0; i < function.params.size(); i++) {
            frame.locals.put(function.params.get(i), Cell.scalar(Ast.Type.INT, args.get(i)));
        }

        int pc = 0;
        while (pc < function.lines.size()) {
            String line = function.lines.get(pc);
            if (line.endsWith(":")) {
                pc++;
                continue;
            }
            if (line.startsWith("declare ")) {
                declare(frame.locals, line);
                pc++;
                continue;
            }
            if (line.startsWith("goto ")) {
                pc = function.label(line.substring("goto ".length()).trim());
                continue;
            }
            if (line.startsWith("if_false ")) {
                String[] parts = line.split("\\s+");
                if (parts.length != 4 || !"goto".equals(parts[2])) {
                    fail("Invalid if_false instruction: " + line);
                }
                pc = truthy(resolveValue(frame, parts[1])) ? pc + 1 : function.label(parts[3]);
                continue;
            }
            if (line.startsWith("param ")) {
                frame.pendingParams.add(resolveValue(frame, line.substring("param ".length()).trim()));
                pc++;
                continue;
            }
            if ("print".equals(line)) {
                if (frame.pendingParams.isEmpty()) {
                    fail("print requires a pending parameter.");
                }
                RuntimeValue value = frame.pendingParams.removeLast();
                stdout.append(value.display()).append('\n');
                pc++;
                continue;
            }
            if (line.startsWith("return")) {
                String valueText = line.length() == "return".length() ? "" : line.substring("return".length()).trim();
                return valueText.isEmpty() ? RuntimeValue.of(Ast.Type.INT, 0) : resolveValue(frame, valueText);
            }
            executeAssignment(frame, line);
            pc++;
        }
        return RuntimeValue.of(Ast.Type.INT, 0);
    }

    private void executeAssignment(Frame frame, String line) {
        int equals = line.indexOf(" = ");
        if (equals < 0) {
            fail("Unsupported IR instruction: " + line);
        }
        String target = line.substring(0, equals).trim();
        String expr = line.substring(equals + 3).trim();

        if (target.endsWith("]")) {
            ArrayTarget arrayTarget = parseArrayTarget(target);
            Cell array = frame.resolve(arrayTarget.name);
            int index = resolveValue(frame, arrayTarget.index).asInt();
            checkArrayIndex(arrayTarget.name, array, index);
            array.values.set(index, cast(evaluateExpression(frame, expr), array.type));
            return;
        }

        RuntimeValue value = evaluateExpression(frame, expr);
        Cell existing = frame.find(target);
        if (existing == null) {
            frame.locals.put(target, Cell.scalar(value.type, value));
        } else {
            if (existing.array) {
                fail("Cannot assign to whole array: " + target);
            }
            existing.value = cast(value, existing.type);
        }
    }

    private RuntimeValue evaluateExpression(Frame frame, String expr) {
        if (expr.startsWith("call ")) {
            String tail = expr.substring("call ".length()).trim();
            String[] parts = tail.split(",");
            if (parts.length != 2) {
                fail("Invalid call expression: " + expr);
            }
            String callee = parts[0].trim();
            int argc = Integer.parseInt(parts[1].trim());
            if (frame.pendingParams.size() < argc) {
                fail("Not enough parameters for call " + callee);
            }
            List<RuntimeValue> args = new ArrayList<>();
            for (int i = 0; i < argc; i++) {
                args.add(0, frame.pendingParams.removeLast());
            }
            return call(callee, args);
        }
        if (expr.startsWith("!") && !expr.startsWith("!=")) {
            return RuntimeValue.of(Ast.Type.BOOL, truthy(resolveValue(frame, expr.substring(1).trim())) ? 0 : 1);
        }
        if (expr.startsWith("-") && !isInteger(expr)) {
            return RuntimeValue.of(Ast.Type.INT, -resolveValue(frame, expr.substring(1).trim()).asInt());
        }
        if (expr.startsWith("+") && !isInteger(expr)) {
            return RuntimeValue.of(Ast.Type.INT, resolveValue(frame, expr.substring(1).trim()).asInt());
        }

        String[] parts = expr.split("\\s+");
        if (parts.length == 3) {
            RuntimeValue left = resolveValue(frame, parts[0]);
            RuntimeValue right = resolveValue(frame, parts[2]);
            int a = left.asInt();
            int b = right.asInt();
            return switch (parts[1]) {
                case "+" -> RuntimeValue.of(Ast.Type.INT, a + b);
                case "-" -> RuntimeValue.of(Ast.Type.INT, a - b);
                case "*" -> RuntimeValue.of(Ast.Type.INT, a * b);
                case "/" -> RuntimeValue.of(Ast.Type.INT, b == 0 ? failValue("Division by zero.") : a / b);
                case "%" -> RuntimeValue.of(Ast.Type.INT, b == 0 ? failValue("Modulo by zero.") : a % b);
                case "<" -> RuntimeValue.of(Ast.Type.BOOL, a < b ? 1 : 0);
                case "<=" -> RuntimeValue.of(Ast.Type.BOOL, a <= b ? 1 : 0);
                case ">" -> RuntimeValue.of(Ast.Type.BOOL, a > b ? 1 : 0);
                case ">=" -> RuntimeValue.of(Ast.Type.BOOL, a >= b ? 1 : 0);
                case "==" -> RuntimeValue.of(Ast.Type.BOOL, a == b ? 1 : 0);
                case "!=" -> RuntimeValue.of(Ast.Type.BOOL, a != b ? 1 : 0);
                case "&&" -> RuntimeValue.of(Ast.Type.BOOL, truthy(left) && truthy(right) ? 1 : 0);
                case "||" -> RuntimeValue.of(Ast.Type.BOOL, truthy(left) || truthy(right) ? 1 : 0);
                default -> throw new CompilerException(Stage.VM, "Unknown operator: " + parts[1], 0, 0);
            };
        }
        return resolveValue(frame, expr);
    }

    private RuntimeValue resolveValue(Frame frame, String text) {
        text = text.trim();
        if ("true".equals(text)) {
            return RuntimeValue.of(Ast.Type.BOOL, 1);
        }
        if ("false".equals(text)) {
            return RuntimeValue.of(Ast.Type.BOOL, 0);
        }
        if (isInteger(text)) {
            return RuntimeValue.of(Ast.Type.INT, Integer.parseInt(text));
        }
        if (text.endsWith("]")) {
            ArrayTarget arrayTarget = parseArrayTarget(text);
            Cell array = frame.resolve(arrayTarget.name);
            int index = resolveValue(frame, arrayTarget.index).asInt();
            checkArrayIndex(arrayTarget.name, array, index);
            return array.values.get(index);
        }
        Cell cell = frame.resolve(text);
        if (cell.array) {
            fail("Array requires an index: " + text);
        }
        return cell.value;
    }

    private void declare(Map<String, Cell> scope, String line) {
        String body = line.substring("declare ".length()).trim();
        String[] parts = body.split("\\s+");
        if (parts.length != 2) {
            fail("Invalid declaration: " + line);
        }
        Ast.Type type = parseType(parts[0]);
        String name = parts[1];
        if (name.endsWith("]")) {
            int left = name.indexOf('[');
            int right = name.indexOf(']');
            String arrayName = name.substring(0, left);
            int size = Integer.parseInt(name.substring(left + 1, right));
            List<RuntimeValue> values = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                values.add(RuntimeValue.defaultValue(type));
            }
            scope.put(arrayName, Cell.array(type, values));
        } else {
            scope.put(name, Cell.scalar(type, RuntimeValue.defaultValue(type)));
        }
    }

    private Ast.Type parseType(String text) {
        return switch (text) {
            case "int" -> Ast.Type.INT;
            case "char" -> Ast.Type.CHAR;
            case "bool" -> Ast.Type.BOOL;
            default -> throw new CompilerException(Stage.VM, "Unknown IR type: " + text, 0, 0);
        };
    }

    private ArrayTarget parseArrayTarget(String text) {
        int left = text.indexOf('[');
        int right = text.lastIndexOf(']');
        if (left <= 0 || right <= left) {
            fail("Invalid array reference: " + text);
        }
        return new ArrayTarget(text.substring(0, left), text.substring(left + 1, right));
    }

    private RuntimeValue cast(RuntimeValue value, Ast.Type type) {
        int raw = value.asInt();
        if (type == Ast.Type.BOOL) {
            return RuntimeValue.of(type, raw == 0 ? 0 : 1);
        }
        if (type == Ast.Type.CHAR) {
            return RuntimeValue.of(type, raw & 0xff);
        }
        return RuntimeValue.of(type, raw);
    }

    private boolean isInteger(String text) {
        return text.matches("-?\\d+");
    }

    private boolean truthy(RuntimeValue value) {
        return value.asInt() != 0;
    }

    private void checkArrayIndex(String name, Cell array, int index) {
        if (!array.array) {
            fail(name + " is not an array.");
        }
        if (index < 0 || index >= array.values.size()) {
            fail("Array index out of bounds: " + name + "[" + index + "]");
        }
    }

    private int failValue(String message) {
        fail(message);
        return 0;
    }

    private void fail(String message) {
        throw new CompilerException(Stage.VM, message, 0, 0);
    }

    private record ArrayTarget(String name, String index) {
    }

    private static class FunctionBlock {
        final String name;
        final List<String> lines = new ArrayList<>();
        final List<String> params = new ArrayList<>();
        final Map<String, Integer> labels = new HashMap<>();

        FunctionBlock(String name) {
            this.name = name;
        }

        void add(String line) {
            if (line.startsWith("param_def ")) {
                params.add(line.substring("param_def ".length()).trim());
                return;
            }
            if (line.endsWith(":")) {
                labels.put(line.substring(0, line.length() - 1), lines.size());
            }
            lines.add(line);
        }

        int label(String label) {
            Integer pc = labels.get(label);
            if (pc == null) {
                throw new CompilerException(Stage.VM, "Unknown label in function " + name + ": " + label, 0, 0);
            }
            return pc;
        }
    }

    private static class Frame {
        final Map<String, Cell> globals;
        final Map<String, Cell> locals = new HashMap<>();
        final ArrayDeque<RuntimeValue> pendingParams = new ArrayDeque<>();

        Frame(Map<String, Cell> globals) {
            this.globals = globals;
        }

        Cell find(String name) {
            Cell local = locals.get(name);
            return local == null ? globals.get(name) : local;
        }

        Cell resolve(String name) {
            Cell cell = find(name);
            if (cell == null) {
                throw new CompilerException(Stage.VM, "Undefined runtime value: " + name, 0, 0);
            }
            return cell;
        }
    }

    private static class Cell {
        final Ast.Type type;
        final boolean array;
        RuntimeValue value;
        List<RuntimeValue> values;

        private Cell(Ast.Type type, boolean array) {
            this.type = type;
            this.array = array;
        }

        static Cell scalar(Ast.Type type, RuntimeValue value) {
            Cell cell = new Cell(type, false);
            cell.value = value;
            return cell;
        }

        static Cell array(Ast.Type type, List<RuntimeValue> values) {
            Cell cell = new Cell(type, true);
            cell.values = values;
            return cell;
        }
    }

    private record RuntimeValue(Ast.Type type, int raw) {
        static RuntimeValue of(Ast.Type type, int raw) {
            return new RuntimeValue(type, raw);
        }

        static RuntimeValue defaultValue(Ast.Type type) {
            return new RuntimeValue(type, 0);
        }

        int asInt() {
            return raw;
        }

        String display() {
            if (type == Ast.Type.BOOL) {
                return raw == 0 ? "false" : "true";
            }
            if (type == Ast.Type.CHAR) {
                return Character.toString((char) raw);
            }
            return Integer.toString(raw);
        }
    }
}

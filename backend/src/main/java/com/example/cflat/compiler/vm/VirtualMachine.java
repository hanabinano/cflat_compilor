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
    private static final int MAX_TRACE_STEPS = 5000;
    private static final int MAX_TRACE_INSTRUCTIONS = 200000;

    private final Map<String, FunctionBlock> functions = new HashMap<>();
    private final Map<String, Cell> globals = new HashMap<>();
    private final StringBuilder stdout = new StringBuilder();
    private String input = "";
    private int inputPos;

    // Time-travel debugging state (null/false when not tracing).
    private boolean tracing;
    private List<DebugTrace.Snapshot> snapshots;
    private boolean truncated;
    private int executedInstructions;

    public VMResult execute(IRProgram program, String stdin) {
        functions.clear();
        globals.clear();
        stdout.setLength(0);
        input = stdin == null ? "" : stdin;
        inputPos = 0;
        tracing = false;
        loadProgram(program.instructions());
        RuntimeValue exit = call("main", List.of());
        return new VMResult(stdout.toString(), "", exit.asInt());
    }

    /**
     * Runs the program while recording a snapshot after every meaningful
     * instruction, producing a rewindable time line for the debugger.
     */
    public DebugTrace trace(IRProgram program, String stdin) {
        functions.clear();
        globals.clear();
        stdout.setLength(0);
        input = stdin == null ? "" : stdin;
        inputPos = 0;
        tracing = true;
        truncated = false;
        executedInstructions = 0;
        snapshots = new ArrayList<>();
        loadProgram(program.instructions());
        RuntimeValue exit = call("main", List.of());
        return new DebugTrace(List.copyOf(snapshots), truncated, exit.asInt());
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
            if (tracing && ++executedInstructions > MAX_TRACE_INSTRUCTIONS) {
                fail("调试执行步数过多（可能存在死循环），已中止。");
            }
            String line = function.lines.get(pc);
            if (line.endsWith(":")) {
                pc++;
                continue;
            }
            if (line.startsWith("declare ")) {
                declare(frame.locals, line);
                recordSnapshot(frame, function.name, line, declareAction(line));
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
                recordSnapshot(frame, function.name, line, "输出");
                pc++;
                continue;
            }
            if (line.startsWith("printf ")) {
                int argc = Integer.parseInt(line.substring("printf ".length()).trim());
                executePrintf(frame, argc);
                recordSnapshot(frame, function.name, line, "输出");
                pc++;
                continue;
            }
            if (line.startsWith("read ")) {
                executeRead(frame, line.substring("read ".length()).trim());
                recordSnapshot(frame, function.name, line, "读取输入 " + line.substring("read ".length()).trim());
                pc++;
                continue;
            }
            if (line.startsWith("return")) {
                String valueText = line.length() == "return".length() ? "" : line.substring("return".length()).trim();
                recordSnapshot(frame, function.name, line, "返回");
                return valueText.isEmpty() ? RuntimeValue.of(Ast.Type.INT, 0) : resolveValue(frame, valueText);
            }
            executeAssignment(frame, line);
            if (!isTempAssignment(line)) {
                recordSnapshot(frame, function.name, line, assignAction(line));
            }
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
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return RuntimeValue.ofString(decodeString(text.substring(1, text.length() - 1)));
        }
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

    private void executePrintf(Frame frame, int argc) {
        if (frame.pendingParams.size() < argc) {
            fail("printf does not have enough arguments.");
        }
        List<RuntimeValue> args = new ArrayList<>();
        for (int i = 0; i < argc; i++) {
            args.add(0, frame.pendingParams.removeLast());
        }
        RuntimeValue first = args.get(0);
        if (first.type() != Ast.Type.STRING) {
            // Defensive: behaves like legacy single-value print.
            for (RuntimeValue value : args) {
                stdout.append(value.display()).append('\n');
            }
            return;
        }
        String format = first.display();
        int argIndex = 1;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '%' && i + 1 < format.length()) {
                char spec = format.charAt(++i);
                if (spec == '%') {
                    stdout.append('%');
                    continue;
                }
                if (argIndex >= args.size()) {
                    fail("printf: missing argument for %" + spec);
                }
                RuntimeValue value = args.get(argIndex++);
                switch (spec) {
                    case 'd' -> stdout.append(value.asInt());
                    case 'c' -> stdout.append((char) value.asInt());
                    case 's' -> stdout.append(value.display());
                    default -> fail("Unsupported printf specifier: %" + spec);
                }
            } else {
                stdout.append(c);
            }
        }
    }

    private void executeRead(Frame frame, String target) {
        int value = readNextInt();
        if (target.endsWith("]")) {
            ArrayTarget arrayTarget = parseArrayTarget(target);
            Cell array = frame.resolve(arrayTarget.name);
            int index = resolveValue(frame, arrayTarget.index).asInt();
            checkArrayIndex(arrayTarget.name, array, index);
            array.values.set(index, cast(RuntimeValue.of(Ast.Type.INT, value), array.type));
            return;
        }
        Cell cell = frame.find(target);
        if (cell == null) {
            frame.locals.put(target, Cell.scalar(Ast.Type.INT, RuntimeValue.of(Ast.Type.INT, value)));
            return;
        }
        if (cell.array) {
            fail("scanf target requires an index: " + target);
        }
        cell.value = cast(RuntimeValue.of(Ast.Type.INT, value), cell.type);
    }

    private int readNextInt() {
        while (inputPos < input.length() && Character.isWhitespace(input.charAt(inputPos))) {
            inputPos++;
        }
        if (inputPos >= input.length()) {
            fail("scanf: no more input available. 请在“标准输入”框中提供足够的数据。");
        }
        int start = inputPos;
        if (input.charAt(inputPos) == '+' || input.charAt(inputPos) == '-') {
            inputPos++;
        }
        while (inputPos < input.length() && Character.isDigit(input.charAt(inputPos))) {
            inputPos++;
        }
        String token = input.substring(start, inputPos);
        if (token.isEmpty() || "+".equals(token) || "-".equals(token)) {
            fail("scanf: expected an integer but found '" + input.charAt(start) + "'.");
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            fail("scanf: invalid integer '" + token + "'.");
            return 0;
        }
    }

    private String decodeString(String encoded) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '\\' && i + 1 < encoded.length()) {
                char next = encoded.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '0' -> '\0';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void recordSnapshot(Frame frame, String function, String line, String action) {
        if (!tracing) {
            return;
        }
        if (snapshots.size() >= MAX_TRACE_STEPS) {
            truncated = true;
            return;
        }
        List<DebugTrace.Variable> variables = new ArrayList<>();
        collectVariables(globals, variables);
        collectVariables(frame.locals, variables);
        snapshots.add(new DebugTrace.Snapshot(
                snapshots.size() + 1, function, line.trim(), action, variables, stdout.toString()));
    }

    private void collectVariables(Map<String, Cell> scope, List<DebugTrace.Variable> out) {
        for (Map.Entry<String, Cell> entry : scope.entrySet()) {
            String name = entry.getKey();
            if (isTemp(name)) {
                continue;
            }
            Cell cell = entry.getValue();
            String type = cell.type.name().toLowerCase();
            if (cell.array) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < cell.values.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(cell.values.get(i).display());
                }
                sb.append(']');
                out.add(new DebugTrace.Variable(name, type, sb.toString(), true));
            } else {
                out.add(new DebugTrace.Variable(name, type, cell.value.display(), false));
            }
        }
    }

    private boolean isTemp(String name) {
        if (name.length() < 2 || name.charAt(0) != 't') {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String declareAction(String line) {
        String body = line.substring("declare ".length()).trim();
        String[] parts = body.split("\\s+");
        String target = parts.length == 2 ? parts[1] : body;
        return "声明 " + target;
    }

    private String assignAction(String line) {
        int equals = line.indexOf(" = ");
        if (equals < 0) {
            return "执行";
        }
        String target = line.substring(0, equals).trim();
        if (isTemp(target)) {
            return "计算";
        }
        return "赋值 " + target;
    }

    private boolean isTempAssignment(String line) {
        int equals = line.indexOf(" = ");
        if (equals < 0) {
            return false;
        }
        return isTemp(line.substring(0, equals).trim());
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

    private record RuntimeValue(Ast.Type type, int raw, String text) {
        static RuntimeValue of(Ast.Type type, int raw) {
            return new RuntimeValue(type, raw, null);
        }

        static RuntimeValue ofString(String text) {
            return new RuntimeValue(Ast.Type.STRING, 0, text);
        }

        static RuntimeValue defaultValue(Ast.Type type) {
            return new RuntimeValue(type, 0, null);
        }

        int asInt() {
            return raw;
        }

        String display() {
            if (type == Ast.Type.STRING) {
                return text == null ? "" : text;
            }
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

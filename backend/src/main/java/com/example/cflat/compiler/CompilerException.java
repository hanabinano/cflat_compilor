package com.example.cflat.compiler;

public class CompilerException extends RuntimeException {
    private final Stage stage;
    private final int line;
    private final int column;

    public CompilerException(Stage stage, String message, int line, int column) {
        super(message);
        this.stage = stage;
        this.line = line;
        this.column = column;
    }

    public Stage stage() {
        return stage;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}

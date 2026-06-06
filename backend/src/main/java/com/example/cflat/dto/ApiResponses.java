package com.example.cflat.dto;

import com.example.cflat.compiler.lexer.Token;

import java.util.List;

public final class ApiResponses {
    private ApiResponses() {
    }

    public record ErrorResponse(boolean success, String stage, String message, int line, int column) {
    }

    public record TokenResponse(boolean success, List<Token> tokens) {
    }

    public record AstResponse(boolean success, Object ast) {
    }

    public record CompileResponse(boolean success, List<String> ir) {
    }

    public record RunResponse(boolean success, List<String> ir, String stdout, String stderr, int exitCode) {
    }
}

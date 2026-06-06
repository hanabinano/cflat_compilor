package com.example.cflat.compiler.lexer;

public record Token(TokenType type, String lexeme, int line, int column) {
}

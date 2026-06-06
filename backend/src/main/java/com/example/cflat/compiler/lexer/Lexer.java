package com.example.cflat.compiler.lexer;

import com.example.cflat.compiler.CompilerException;
import com.example.cflat.compiler.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("int", TokenType.KW_INT);
        KEYWORDS.put("char", TokenType.KW_CHAR);
        KEYWORDS.put("bool", TokenType.KW_BOOL);
        KEYWORDS.put("void", TokenType.KW_VOID);
        KEYWORDS.put("if", TokenType.KW_IF);
        KEYWORDS.put("else", TokenType.KW_ELSE);
        KEYWORDS.put("while", TokenType.KW_WHILE);
        KEYWORDS.put("for", TokenType.KW_FOR);
        KEYWORDS.put("break", TokenType.KW_BREAK);
        KEYWORDS.put("continue", TokenType.KW_CONTINUE);
        KEYWORDS.put("return", TokenType.KW_RETURN);
        KEYWORDS.put("true", TokenType.KW_TRUE);
        KEYWORDS.put("false", TokenType.KW_FALSE);
        KEYWORDS.put("printf", TokenType.KW_PRINTF);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int index;
    private int line = 1;
    private int column = 1;

    public Lexer(String source) {
        this.source = source == null ? "" : source;
    }

    public List<Token> tokenize() {
        while (!isAtEnd()) {
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        int startLine = line;
        int startColumn = column - 1;
        switch (c) {
            case ' ', '\r', '\t' -> {
            }
            case '\n' -> newLine();
            case '(' -> add(TokenType.LPAREN, "(", startLine, startColumn);
            case ')' -> add(TokenType.RPAREN, ")", startLine, startColumn);
            case '{' -> add(TokenType.LBRACE, "{", startLine, startColumn);
            case '}' -> add(TokenType.RBRACE, "}", startLine, startColumn);
            case '[' -> add(TokenType.LBRACKET, "[", startLine, startColumn);
            case ']' -> add(TokenType.RBRACKET, "]", startLine, startColumn);
            case ';' -> add(TokenType.SEMICOLON, ";", startLine, startColumn);
            case ',' -> add(TokenType.COMMA, ",", startLine, startColumn);
            case '+' -> add(TokenType.PLUS, "+", startLine, startColumn);
            case '-' -> add(TokenType.MINUS, "-", startLine, startColumn);
            case '*' -> add(TokenType.STAR, "*", startLine, startColumn);
            case '%' -> add(TokenType.PERCENT, "%", startLine, startColumn);
            case '!' -> {
                boolean two = match('=');
                add(two ? TokenType.NEQ : TokenType.BANG, two ? "!=" : "!", startLine, startColumn);
            }
            case '=' -> {
                boolean two = match('=');
                add(two ? TokenType.EQEQ : TokenType.ASSIGN, two ? "==" : "=", startLine, startColumn);
            }
            case '<' -> {
                boolean two = match('=');
                add(two ? TokenType.LTE : TokenType.LT, two ? "<=" : "<", startLine, startColumn);
            }
            case '>' -> {
                boolean two = match('=');
                add(two ? TokenType.GTE : TokenType.GT, two ? ">=" : ">", startLine, startColumn);
            }
            case '&' -> {
                if (match('&')) {
                    add(TokenType.ANDAND, "&&", startLine, startColumn);
                } else {
                    error("Unexpected '&'. Did you mean '&&'?", startLine, startColumn);
                }
            }
            case '|' -> {
                if (match('|')) {
                    add(TokenType.OROR, "||", startLine, startColumn);
                } else {
                    error("Unexpected '|'. Did you mean '||'?", startLine, startColumn);
                }
            }
            case '/' -> scanSlash(startLine, startColumn);
            case '\'' -> scanChar(startLine, startColumn);
            default -> {
                if (isDigit(c)) {
                    scanNumber(startLine, startColumn);
                } else if (isAlpha(c) || c == '_') {
                    scanIdentifier(startLine, startColumn);
                } else {
                    error("Unexpected character: " + c, startLine, startColumn);
                }
            }
        }
    }

    private void scanSlash(int startLine, int startColumn) {
        if (match('/')) {
            while (!isAtEnd() && peek() != '\n') {
                advance();
            }
            return;
        }
        if (match('*')) {
            while (!isAtEnd()) {
                if (peek() == '*' && peekNext() == '/') {
                    advance();
                    advance();
                    return;
                }
                char c = advance();
                if (c == '\n') {
                    newLine();
                }
            }
            error("Unterminated block comment.", startLine, startColumn);
        }
        add(TokenType.SLASH, "/", startLine, startColumn);
    }

    private void scanNumber(int startLine, int startColumn) {
        int start = index - 1;
        while (!isAtEnd() && isDigit(peek())) {
            advance();
        }
        add(TokenType.INT_LITERAL, source.substring(start, index), startLine, startColumn);
    }

    private void scanIdentifier(int startLine, int startColumn) {
        int start = index - 1;
        while (!isAtEnd() && (isAlpha(peek()) || isDigit(peek()) || peek() == '_')) {
            advance();
        }
        String text = source.substring(start, index);
        add(KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER), text, startLine, startColumn);
    }

    private void scanChar(int startLine, int startColumn) {
        StringBuilder value = new StringBuilder("'");
        if (isAtEnd() || peek() == '\n') {
            error("Unterminated char literal.", startLine, startColumn);
        }
        char c = advance();
        value.append(c);
        if (c == '\\') {
            if (isAtEnd()) {
                error("Unterminated char escape.", startLine, startColumn);
            }
            char escaped = advance();
            if ("ntr0'\\".indexOf(escaped) < 0) {
                error("Unsupported char escape: \\" + escaped, startLine, startColumn);
            }
            value.append(escaped);
        }
        if (!match('\'')) {
            error("Char literal must contain exactly one character.", startLine, startColumn);
        }
        value.append('\'');
        add(TokenType.CHAR_LITERAL, value.toString(), startLine, startColumn);
    }

    private void add(TokenType type, String lexeme, int tokenLine, int tokenColumn) {
        tokens.add(new Token(type, lexeme, tokenLine, tokenColumn));
    }

    private char advance() {
        column++;
        return source.charAt(index++);
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(index) != expected) {
            return false;
        }
        index++;
        column++;
        return true;
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(index);
    }

    private char peekNext() {
        return index + 1 >= source.length() ? '\0' : source.charAt(index + 1);
    }

    private boolean isAtEnd() {
        return index >= source.length();
    }

    private void newLine() {
        line++;
        column = 1;
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private void error(String message, int errorLine, int errorColumn) {
        throw new CompilerException(Stage.LEXER, message, errorLine, errorColumn);
    }
}

package com.example.cflat.compiler.parser;

import com.example.cflat.compiler.CompilerException;
import com.example.cflat.compiler.Stage;
import com.example.cflat.compiler.ast.Ast;
import com.example.cflat.compiler.lexer.Token;
import com.example.cflat.compiler.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int current;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Ast.Program parse() {
        List<Ast.FunctionDef> functions = new ArrayList<>();
        List<Ast.VarDecl> globals = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            Ast.Type type = parseType();
            Token name = consume(TokenType.IDENTIFIER, "Expected identifier after type.");
            if (match(TokenType.LPAREN)) {
                functions.add(parseFunction(type, name.lexeme()));
            } else {
                globals.add(parseDeclarationAfterFirst(type, name));
            }
        }
        return new Ast.Program(functions, globals);
    }

    private Ast.FunctionDef parseFunction(Ast.Type returnType, String name) {
        List<Ast.Parameter> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                Ast.Type type = parseType();
                Token paramName = consume(TokenType.IDENTIFIER, "Expected parameter name.");
                params.add(new Ast.Parameter(type, paramName.lexeme()));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after parameters.");
        Ast.Block body = parseBlock();
        return new Ast.FunctionDef(returnType, name, params, body);
    }

    private Ast.VarDecl parseDeclaration() {
        Ast.Type type = parseType();
        Token first = consume(TokenType.IDENTIFIER, "Expected variable name.");
        return parseDeclarationAfterFirst(type, first);
    }

    private Ast.VarDecl parseDeclarationAfterFirst(Ast.Type type, Token first) {
        List<Ast.Declarator> declarators = new ArrayList<>();
        declarators.add(parseDeclaratorTail(first));
        while (match(TokenType.COMMA)) {
            Token name = consume(TokenType.IDENTIFIER, "Expected variable name.");
            declarators.add(parseDeclaratorTail(name));
        }
        consume(TokenType.SEMICOLON, "Expected ';' after declaration.");
        return new Ast.VarDecl(type, declarators);
    }

    private Ast.VarDecl parseDeclarationWithoutSemicolon() {
        Ast.Type type = parseType();
        List<Ast.Declarator> declarators = new ArrayList<>();
        Token first = consume(TokenType.IDENTIFIER, "Expected variable name.");
        declarators.add(parseDeclaratorTail(first));
        while (match(TokenType.COMMA)) {
            Token name = consume(TokenType.IDENTIFIER, "Expected variable name.");
            declarators.add(parseDeclaratorTail(name));
        }
        return new Ast.VarDecl(type, declarators);
    }

    private Ast.Declarator parseDeclaratorTail(Token name) {
        Integer arraySize = null;
        Ast.Expression initializer = null;
        if (match(TokenType.LBRACKET)) {
            Token size = consume(TokenType.INT_LITERAL, "Expected integer array size.");
            arraySize = Integer.parseInt(size.lexeme());
            consume(TokenType.RBRACKET, "Expected ']' after array size.");
        }
        if (match(TokenType.ASSIGN)) {
            initializer = expression();
        }
        return new Ast.Declarator(name.lexeme(), arraySize, initializer);
    }

    private Ast.Block parseBlock() {
        consume(TokenType.LBRACE, "Expected '{' before block.");
        List<Ast.Statement> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            statements.add(blockItem());
        }
        consume(TokenType.RBRACE, "Expected '}' after block.");
        return new Ast.Block(statements);
    }

    private Ast.Statement blockItem() {
        if (isType(peek().type())) {
            return parseDeclaration();
        }
        return statement();
    }

    private Ast.Statement statement() {
        if (check(TokenType.LBRACE)) {
            return parseBlock();
        }
        if (match(TokenType.KW_IF)) {
            consume(TokenType.LPAREN, "Expected '(' after if.");
            Ast.Expression condition = expression();
            consume(TokenType.RPAREN, "Expected ')' after if condition.");
            Ast.Statement thenBranch = statement();
            Ast.Statement elseBranch = match(TokenType.KW_ELSE) ? statement() : null;
            return new Ast.IfStmt(condition, thenBranch, elseBranch);
        }
        if (match(TokenType.KW_WHILE)) {
            consume(TokenType.LPAREN, "Expected '(' after while.");
            Ast.Expression condition = expression();
            consume(TokenType.RPAREN, "Expected ')' after while condition.");
            return new Ast.WhileStmt(condition, statement());
        }
        if (match(TokenType.KW_FOR)) {
            consume(TokenType.LPAREN, "Expected '(' after for.");
            Ast.Statement init = null;
            if (!check(TokenType.SEMICOLON)) {
                init = isType(peek().type())
                        ? parseDeclarationWithoutSemicolon()
                        : new Ast.ExprStmt(expression());
            }
            consume(TokenType.SEMICOLON, "Expected ';' after for initializer.");
            Ast.Expression condition = check(TokenType.SEMICOLON) ? null : expression();
            consume(TokenType.SEMICOLON, "Expected ';' after for condition.");
            Ast.Expression update = check(TokenType.RPAREN) ? null : expression();
            consume(TokenType.RPAREN, "Expected ')' after for clauses.");
            return new Ast.ForStmt(init, condition, update, statement());
        }
        if (match(TokenType.KW_BREAK)) {
            consume(TokenType.SEMICOLON, "Expected ';' after break.");
            return new Ast.BreakStmt();
        }
        if (match(TokenType.KW_CONTINUE)) {
            consume(TokenType.SEMICOLON, "Expected ';' after continue.");
            return new Ast.ContinueStmt();
        }
        if (match(TokenType.KW_RETURN)) {
            Ast.Expression value = check(TokenType.SEMICOLON) ? null : expression();
            consume(TokenType.SEMICOLON, "Expected ';' after return.");
            return new Ast.ReturnStmt(value);
        }
        Ast.Expression expr = check(TokenType.SEMICOLON) ? null : expression();
        consume(TokenType.SEMICOLON, "Expected ';' after expression.");
        return new Ast.ExprStmt(expr);
    }

    private Ast.Expression expression() {
        return assignment();
    }

    private Ast.Expression assignment() {
        Ast.Expression expr = logicalOr();
        if (match(TokenType.ASSIGN)) {
            Ast.Expression value = assignment();
            return new Ast.Assign(expr, value);
        }
        if (match(TokenType.PLUSEQ, TokenType.MINUSEQ, TokenType.STAREQ,
                TokenType.SLASHEQ, TokenType.PERCENTEQ)) {
            Token op = previous();
            if (!(expr instanceof Ast.Variable) && !(expr instanceof Ast.ArrayAccess)) {
                error("Left side of '" + op.lexeme() + "' must be a variable or array element.", op);
            }
            String binOp = switch (op.type()) {
                case PLUSEQ -> "+";
                case MINUSEQ -> "-";
                case STAREQ -> "*";
                case SLASHEQ -> "/";
                default -> "%";
            };
            Ast.Expression value = assignment();
            return new Ast.Assign(expr, new Ast.Binary(expr, binOp, value));
        }
        return expr;
    }

    private Ast.Expression logicalOr() {
        Ast.Expression expr = logicalAnd();
        while (match(TokenType.OROR)) {
            expr = new Ast.Binary(expr, "||", logicalAnd());
        }
        return expr;
    }

    private Ast.Expression logicalAnd() {
        Ast.Expression expr = equality();
        while (match(TokenType.ANDAND)) {
            expr = new Ast.Binary(expr, "&&", equality());
        }
        return expr;
    }

    private Ast.Expression equality() {
        Ast.Expression expr = relational();
        while (match(TokenType.EQEQ, TokenType.NEQ)) {
            Token op = previous();
            expr = new Ast.Binary(expr, op.lexeme(), relational());
        }
        return expr;
    }

    private Ast.Expression relational() {
        Ast.Expression expr = additive();
        while (match(TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE)) {
            Token op = previous();
            expr = new Ast.Binary(expr, op.lexeme(), additive());
        }
        return expr;
    }

    private Ast.Expression additive() {
        Ast.Expression expr = multiplicative();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token op = previous();
            expr = new Ast.Binary(expr, op.lexeme(), multiplicative());
        }
        return expr;
    }

    private Ast.Expression multiplicative() {
        Ast.Expression expr = unary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token op = previous();
            expr = new Ast.Binary(expr, op.lexeme(), unary());
        }
        return expr;
    }

    private Ast.Expression unary() {
        if (match(TokenType.BANG, TokenType.MINUS, TokenType.PLUS)) {
            Token op = previous();
            return new Ast.Unary(op.lexeme(), unary());
        }
        if (match(TokenType.PLUSPLUS, TokenType.MINUSMINUS)) {
            Token op = previous();
            Ast.Expression target = unary();
            return desugarIncrement(target, op);
        }
        return postfix();
    }

    private Ast.Expression desugarIncrement(Ast.Expression target, Token op) {
        if (!(target instanceof Ast.Variable) && !(target instanceof Ast.ArrayAccess)) {
            error("Operand of '" + op.lexeme() + "' must be a variable or array element.", op);
        }
        String binOp = op.type() == TokenType.PLUSPLUS ? "+" : "-";
        Ast.Expression one = new Ast.Literal(1, Ast.Type.INT);
        return new Ast.Assign(target, new Ast.Binary(target, binOp, one));
    }

    private Ast.Expression postfix() {
        Ast.Expression expr = primary();
        while (true) {
            if (match(TokenType.LPAREN)) {
                if (!(expr instanceof Ast.Variable)) {
                    error("Only identifiers can be called as functions.", previous());
                }
                Ast.Variable variable = (Ast.Variable) expr;
                List<Ast.Expression> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    do {
                        args.add(expression());
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RPAREN, "Expected ')' after arguments.");
                expr = new Ast.Call(variable.name(), args);
            } else if (match(TokenType.LBRACKET)) {
                if (!(expr instanceof Ast.Variable)) {
                    error("Only identifiers can be indexed.", previous());
                }
                Ast.Variable variable = (Ast.Variable) expr;
                Ast.Expression index = expression();
                consume(TokenType.RBRACKET, "Expected ']' after array index.");
                expr = new Ast.ArrayAccess(variable.name(), index);
            } else if (check(TokenType.PLUSPLUS) || check(TokenType.MINUSMINUS)) {
                Token op = advance();
                expr = desugarIncrement(expr, op);
            } else {
                return expr;
            }
        }
    }

    private Ast.Expression primary() {
        if (match(TokenType.INT_LITERAL)) {
            return new Ast.Literal(Integer.parseInt(previous().lexeme()), Ast.Type.INT);
        }
        if (match(TokenType.CHAR_LITERAL)) {
            return new Ast.Literal(parseChar(previous().lexeme()), Ast.Type.CHAR);
        }
        if (match(TokenType.STRING_LITERAL)) {
            return new Ast.StringLiteral(parseString(previous().lexeme()));
        }
        if (match(TokenType.KW_TRUE)) {
            return new Ast.Literal(true, Ast.Type.BOOL);
        }
        if (match(TokenType.KW_FALSE)) {
            return new Ast.Literal(false, Ast.Type.BOOL);
        }
        if (match(TokenType.IDENTIFIER, TokenType.KW_PRINTF, TokenType.KW_SCANF)) {
            return new Ast.Variable(previous().lexeme());
        }
        if (match(TokenType.LPAREN)) {
            Ast.Expression expr = expression();
            consume(TokenType.RPAREN, "Expected ')' after expression.");
            return expr;
        }
        error("Expected expression.", peek());
        return null;
    }

    private int parseChar(String text) {
        String body = text.substring(1, text.length() - 1);
        if (!body.startsWith("\\")) {
            return body.charAt(0);
        }
        return switch (body.charAt(1)) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '0' -> '\0';
            case '\'' -> '\'';
            case '\\' -> '\\';
            default -> body.charAt(1);
        };
    }

    private String parseString(String text) {
        String body = text.substring(1, text.length() - 1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '0' -> '\0';
                    case '"' -> '"';
                    case '\'' -> '\'';
                    case '\\' -> '\\';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private Ast.Type parseType() {
        if (match(TokenType.KW_INT)) {
            return Ast.Type.INT;
        }
        if (match(TokenType.KW_CHAR)) {
            return Ast.Type.CHAR;
        }
        if (match(TokenType.KW_BOOL)) {
            return Ast.Type.BOOL;
        }
        if (match(TokenType.KW_VOID)) {
            return Ast.Type.VOID;
        }
        error("Expected type.", peek());
        return null;
    }

    private boolean isType(TokenType type) {
        return type == TokenType.KW_INT || type == TokenType.KW_CHAR || type == TokenType.KW_BOOL || type == TokenType.KW_VOID;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        error(message, peek());
        return null;
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private Token advance() {
        if (!check(TokenType.EOF)) {
            current++;
        }
        return previous();
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private void error(String message, Token token) {
        throw new CompilerException(Stage.PARSER, message, token.line(), token.column());
    }
}

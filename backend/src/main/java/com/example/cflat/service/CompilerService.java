package com.example.cflat.service;

import com.example.cflat.compiler.ast.Ast;
import com.example.cflat.compiler.ir.IRGenerator;
import com.example.cflat.compiler.ir.IRProgram;
import com.example.cflat.compiler.lexer.Lexer;
import com.example.cflat.compiler.lexer.Token;
import com.example.cflat.compiler.parser.Parser;
import com.example.cflat.compiler.semantic.SemanticAnalyzer;
import com.example.cflat.compiler.vm.DebugTrace;
import com.example.cflat.compiler.vm.VMResult;
import com.example.cflat.compiler.vm.VirtualMachine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompilerService {
    public List<Token> lex(String code) {
        return new Lexer(code).tokenize();
    }

    public Ast.Program parse(String code) {
        return new Parser(lex(code)).parse();
    }

    public IRProgram compile(String code) {
        Ast.Program program = parse(code);
        new SemanticAnalyzer().analyze(program);
        return new IRGenerator().generate(program);
    }

    public RunBundle run(String code, String stdin) {
        IRProgram ir = compile(code);
        VMResult result = new VirtualMachine().execute(ir, stdin == null ? "" : stdin);
        return new RunBundle(ir, result);
    }

    public DebugBundle debug(String code, String stdin) {
        IRProgram ir = compile(code);
        DebugTrace trace = new VirtualMachine().trace(ir, stdin == null ? "" : stdin);
        return new DebugBundle(ir, trace);
    }

    public record RunBundle(IRProgram ir, VMResult result) {
    }

    public record DebugBundle(IRProgram ir, DebugTrace trace) {
    }
}

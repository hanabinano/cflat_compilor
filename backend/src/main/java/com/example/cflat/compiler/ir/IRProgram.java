package com.example.cflat.compiler.ir;

import com.example.cflat.compiler.ast.Ast;

import java.util.List;

public record IRProgram(Ast.Program ast, List<String> instructions) {
}

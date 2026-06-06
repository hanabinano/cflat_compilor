package com.example.cflat.controller;

import com.example.cflat.compiler.CompilerException;
import com.example.cflat.compiler.ir.IRProgram;
import com.example.cflat.dto.ApiResponses;
import com.example.cflat.dto.CompileRequest;
import com.example.cflat.dto.RunRequest;
import com.example.cflat.service.CompilerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CompilerController {
    private final CompilerService compilerService;

    public CompilerController(CompilerService compilerService) {
        this.compilerService = compilerService;
    }

    @PostMapping("/lexer")
    public ApiResponses.TokenResponse lexer(@RequestBody CompileRequest request) {
        return new ApiResponses.TokenResponse(true, compilerService.lex(request.code()));
    }

    @PostMapping("/parser")
    public ApiResponses.AstResponse parser(@RequestBody CompileRequest request) {
        return new ApiResponses.AstResponse(true, compilerService.parse(request.code()));
    }

    @PostMapping("/compile")
    public ApiResponses.CompileResponse compile(@RequestBody CompileRequest request) {
        IRProgram program = compilerService.compile(request.code());
        return new ApiResponses.CompileResponse(true, program.instructions());
    }

    @PostMapping("/run")
    public ApiResponses.RunResponse run(@RequestBody RunRequest request) {
        CompilerService.RunBundle bundle = compilerService.run(request.code(), request.stdin());
        return new ApiResponses.RunResponse(true, bundle.ir().instructions(), bundle.result().stdout(),
                bundle.result().stderr(), bundle.result().exitCode());
    }

    @ExceptionHandler(CompilerException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> compilerError(CompilerException exception) {
        return ResponseEntity.badRequest().body(new ApiResponses.ErrorResponse(false, exception.stage().name(),
                exception.getMessage(), exception.line(), exception.column()));
    }
}

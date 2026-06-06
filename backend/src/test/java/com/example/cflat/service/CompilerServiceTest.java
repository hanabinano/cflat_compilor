package com.example.cflat.service;

import com.example.cflat.compiler.CompilerException;
import com.example.cflat.compiler.lexer.Token;
import com.example.cflat.compiler.vm.VMResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompilerServiceTest {
    private final CompilerService compilerService = new CompilerService();

    @Test
    void lexesKeywordsIdentifiersAndOperators() {
        List<Token> tokens = compilerService.lex("int main() { return 1 + 2; }");

        assertThat(tokens)
                .extracting(Token::lexeme)
                .containsSequence("int", "main", "(", ")", "{", "return", "1", "+", "2", ";", "}");
    }

    @Test
    void compilesFunctionCallToReadableIr() {
        String code = """
                int add(int a, int b) {
                    return a + b;
                }

                int main() {
                    int result = add(2, 3);
                    printf(result);
                    return 0;
                }
                """;

        List<String> ir = compilerService.compile(code).instructions();

        assertThat(ir).contains("function add", "function main", "print");
        assertThat(ir.stream().anyMatch(line -> line.contains("call add, 2"))).isTrue();
    }

    @Test
    void runsLoopAndArrayProgram() {
        String code = """
                int main() {
                    int arr[5];
                    int i = 0;
                    int sum = 0;

                    while (i < 5) {
                        arr[i] = i + 1;
                        sum = sum + arr[i];
                        i = i + 1;
                    }

                    printf(sum);
                    return 0;
                }
                """;

        VMResult result = compilerService.run(code, "").result();

        assertThat(result.stdout()).isEqualTo("15\n");
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void runsForContinueAndWhileBreak() {
        String code = """
                int main() {
                    int i;
                    int sum = 0;

                    for (i = 0; i < 10; i = i + 1) {
                        if (i == 5) {
                            continue;
                        }
                        sum = sum + i;
                    }

                    while (true) {
                        if (sum == 40) {
                            break;
                        }
                        sum = sum + 1;
                    }

                    printf(sum);
                    return 0;
                }
                """;

        VMResult result = compilerService.run(code, "").result();

        assertThat(result.stdout()).isEqualTo("40\n");
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void reportsSemanticErrors() {
        String code = "int main() { break; return 0; }";

        assertThatThrownBy(() -> compilerService.compile(code))
                .isInstanceOf(CompilerException.class)
                .hasMessageContaining("inside a loop");
    }
}

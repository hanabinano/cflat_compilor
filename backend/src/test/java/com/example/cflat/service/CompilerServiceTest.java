package com.example.cflat.service;

import com.example.cflat.compiler.CompilerException;
import com.example.cflat.compiler.lexer.Token;
import com.example.cflat.compiler.vm.DebugTrace;
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

    @Test
    void readsInputWithScanf() {
        String code = """
                int main() {
                    int a;
                    int b;
                    scanf(a, b);
                    printf("%d\\n", a + b);
                    return 0;
                }
                """;

        VMResult result = compilerService.run(code, "3 5").result();

        assertThat(result.stdout()).isEqualTo("8\n");
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void scanfReadsIntoArrayElements() {
        String code = """
                int main() {
                    int data[3];
                    int i;
                    int total = 0;
                    for (i = 0; i < 3; i = i + 1) {
                        scanf(data[i]);
                        total = total + data[i];
                    }
                    printf("%d\\n", total);
                    return 0;
                }
                """;

        VMResult result = compilerService.run(code, "10\n20\n30").result();

        assertThat(result.stdout()).isEqualTo("60\n");
    }

    @Test
    void printfFormatsMultipleArguments() {
        String code = """
                int main() {
                    int year = 2026;
                    char grade = 'A';
                    printf("year=%d grade=%c %d%%\\n", year, grade, 100);
                    return 0;
                }
                """;

        VMResult result = compilerService.run(code, "").result();

        assertThat(result.stdout()).isEqualTo("year=2026 grade=A 100%\n");
    }

    @Test
    void singleArgumentPrintfStillAppendsNewline() {
        String code = """
                int main() {
                    int x = 42;
                    printf(x);
                    return 0;
                }
                """;

        VMResult result = compilerService.run(code, "").result();

        assertThat(result.stdout()).isEqualTo("42\n");
    }

    @Test
    void supportsIncrementAndCompoundAssignment() {
        String code = """
                int main() {
                    int sum = 0;
                    int i;
                    for (i = 0; i < 5; i++) {
                        sum += i;
                    }
                    sum *= 2;
                    printf("%d\\n", sum);
                    return 0;
                }
                """;

        VMResult result = compilerService.run(code, "").result();

        assertThat(result.stdout()).isEqualTo("20\n");
    }

    @Test
    void prefixIncrementUpdatesVariable() {
        String code = """
                int main() {
                    int x = 5;
                    ++x;
                    --x;
                    x -= 2;
                    printf("%d\\n", x);
                    return 0;
                }
                """;

        VMResult result = compilerService.run(code, "").result();

        assertThat(result.stdout()).isEqualTo("3\n");
    }

    @Test
    void rejectsScanfIntoNonLValue() {
        String code = """
                int main() {
                    scanf(1 + 2);
                    return 0;
                }
                """;

        assertThatThrownBy(() -> compilerService.compile(code))
                .isInstanceOf(CompilerException.class)
                .hasMessageContaining("scanf target");
    }

    @Test
    void rejectsPrintfArgumentCountMismatch() {
        String code = """
                int main() {
                    printf("%d %d\\n", 1);
                    return 0;
                }
                """;

        assertThatThrownBy(() -> compilerService.compile(code))
                .isInstanceOf(CompilerException.class)
                .hasMessageContaining("format expects");
    }

    @Test
    void lexesStringAndCompoundOperators() {
        List<Token> tokens = compilerService.lex("printf(\"hi\"); x += 1; y++;");

        assertThat(tokens)
                .extracting(Token::lexeme)
                .contains("\"hi\"", "+=", "++");
    }

    @Test
    void debugTraceRecordsVariableTimeline() {
        String code = """
                int main() {
                    int sum = 0;
                    int i;
                    for (i = 0; i < 3; i = i + 1) {
                        sum = sum + i;
                    }
                    printf("%d\\n", sum);
                    return 0;
                }
                """;

        DebugTrace trace = compilerService.debug(code, "").trace();

        // There should be a sequence of snapshots, not truncated for such a small program.
        assertThat(trace.truncated()).isFalse();
        assertThat(trace.snapshots()).isNotEmpty();
        assertThat(trace.exitCode()).isZero();

        // Each snapshot carries cumulative stdout; the last one shows the printed result.
        DebugTrace.Snapshot last = trace.snapshots().get(trace.snapshots().size() - 1);
        assertThat(last.stdout()).contains("3");

        // The final value of sum (0+1+2 = 3) must appear in the timeline.
        boolean sumReachedThree = trace.snapshots().stream()
                .flatMap(s -> s.variables().stream())
                .anyMatch(v -> v.name().equals("sum") && v.value().equals("3"));
        assertThat(sumReachedThree).isTrue();

        // Temporaries (t0, t1, ...) must not leak into the variable view.
        boolean hasTemp = trace.snapshots().stream()
                .flatMap(s -> s.variables().stream())
                .anyMatch(v -> v.name().matches("t\\d+"));
        assertThat(hasTemp).isFalse();
    }

    @Test
    void debugTraceCapturesArrayState() {
        String code = """
                int main() {
                    int arr[3];
                    int i;
                    for (i = 0; i < 3; i = i + 1) {
                        arr[i] = i * i;
                    }
                    return 0;
                }
                """;

        DebugTrace trace = compilerService.debug(code, "").trace();

        boolean arrayShown = trace.snapshots().stream()
                .flatMap(s -> s.variables().stream())
                .anyMatch(v -> v.name().equals("arr") && v.array() && v.value().contains("4"));
        assertThat(arrayShown).isTrue();
    }
}

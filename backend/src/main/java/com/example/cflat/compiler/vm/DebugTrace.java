package com.example.cflat.compiler.vm;

import java.util.List;

/**
 * Time-travel debugging trace: an ordered list of execution snapshots.
 * Each snapshot captures the visible program state right after a meaningful
 * instruction (assignment, read, print, declaration) so the UI can "rewind".
 */
public record DebugTrace(List<Snapshot> snapshots, boolean truncated, int exitCode) {

    /**
     * One frame of the time line.
     *
     * @param step      1-based index in the trace
     * @param function  the function currently executing
     * @param line      the IR instruction that just ran (what the user "sees")
     * @param action    a short human label for what happened (e.g. "赋值 sum")
     * @param variables current variables visible in this frame (name -> rendered value)
     * @param stdout    cumulative program output up to and including this step
     */
    public record Snapshot(int step,
                           String function,
                           String line,
                           String action,
                           List<Variable> variables,
                           String stdout) {
    }

    /**
     * A single variable's value at a point in time. Arrays render their elements.
     */
    public record Variable(String name, String type, String value, boolean array) {
    }
}

package com.example.teamemc.emc;

import java.util.OptionalLong;

public final class EmcMath {
    private EmcMath() {
    }

    public static OptionalLong multiplyExact(long left, long right) {
        try {
            return OptionalLong.of(Math.multiplyExact(left, right));
        } catch (ArithmeticException exception) {
            return OptionalLong.empty();
        }
    }

    public static OptionalLong addExact(long left, long right) {
        try {
            return OptionalLong.of(Math.addExact(left, right));
        } catch (ArithmeticException exception) {
            return OptionalLong.empty();
        }
    }
}

package com.lhj.jizhang.common.util;

import java.util.UUID;

public final class BusinessIdGenerator {
    private BusinessIdGenerator() {
    }

    public static String next(String prefix) {
        String value = UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        return prefix + value;
    }
}

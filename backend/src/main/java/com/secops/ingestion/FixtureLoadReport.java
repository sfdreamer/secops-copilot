package com.secops.ingestion;

import java.util.List;

/** 启动加载的明确结果，用于日志与测试断言，不静默吞掉问题（DATABASE.md 10 节）。 */
public record FixtureLoadReport(
        int readCount,
        int acceptedCount,
        int duplicateCount,
        int rejectedCount,
        List<String> messages
) {
}

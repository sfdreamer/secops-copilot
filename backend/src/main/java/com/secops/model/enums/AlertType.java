package com.secops.model.enums;

/** DATABASE.md 4 节 alertType 枚举。本期仅 SSH_BRUTE_FORCE 注册分析策略。 */
public enum AlertType {
    SSH_BRUTE_FORCE,
    PORT_SCAN,
    SQL_INJECTION,
    MALWARE_ACTIVITY,
    UNKNOWN
}

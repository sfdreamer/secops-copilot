package com.secops.ingestion;

import com.secops.model.Alert;
import com.secops.model.enums.AlertCategory;
import com.secops.model.enums.AlertType;
import com.secops.model.enums.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验必填字段与枚举（DATABASE.md 5.1 / 4 节）。
 * sourceIp、targetAsset、attributes.protocol 是可选的聚合关键字段：
 * 缺失不判为非法告警，由阶段二标记为「数据不完整」并单独成事件。
 */
@Component
public class AlertValidator {

    public record Issue(int index, String alertId, String reason) {
    }

    public record Result(List<Alert> accepted, List<Issue> rejected) {
    }

    public Result validate(List<Alert> alerts) {
        List<Alert> accepted = new ArrayList<>();
        List<Issue> rejected = new ArrayList<>();
        if (alerts == null) {
            return new Result(accepted, rejected);
        }
        for (int i = 0; i < alerts.size(); i++) {
            Alert alert = alerts.get(i);
            String reason = rejectionReason(alert);
            if (reason == null) {
                accepted.add(alert);
            } else {
                rejected.add(new Issue(i, alert == null ? null : alert.alertId(), reason));
            }
        }
        return new Result(List.copyOf(accepted), List.copyOf(rejected));
    }

    private static String rejectionReason(Alert a) {
        if (a == null) {
            return "告警记录为 null";
        }
        if (isBlank(a.alertId())) {
            return "缺少必填字段 alertId";
        }
        if (isBlank(a.alertType()) || !isEnumValue(AlertType.class, a.alertType())) {
            return "alertType 非法: " + a.alertType();
        }
        if (isBlank(a.category()) || !isEnumValue(AlertCategory.class, a.category())) {
            return "category 非法: " + a.category();
        }
        if (isBlank(a.severity()) || !isEnumValue(Severity.class, a.severity())) {
            return "severity 非法: " + a.severity();
        }
        if (a.timestamp() == null) {
            return "缺少必填字段 timestamp";
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isEnumValue(Class<? extends Enum<?>> enumType, String value) {
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}

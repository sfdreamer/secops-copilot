package com.secops.ingestion;

import com.secops.model.Alert;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 标准化：去除首尾空白、把类型化 attributes 固定为不可变 Map。不猜测或补默认值。 */
@Component
public class AlertNormalizer {

    public Alert normalize(Alert raw) {
        return new Alert(
                trim(raw.alertId()),
                trim(raw.scenarioId()),
                trim(raw.alertType()),
                trim(raw.category()),
                raw.timestamp(),
                trim(raw.sourceIp()),
                trim(raw.targetIp()),
                trim(raw.targetAsset()),
                trim(raw.severity()),
                trim(raw.sourceSystem()),
                immutableAttributes(raw.attributes()),
                raw.rawLog()
        );
    }

    private static Map<String, Object> immutableAttributes(Map<String, Object> attributes) {
        return attributes == null ? null : Map.copyOf(attributes);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

package com.secops.repository;

import com.secops.model.Alert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 接口签名以 DATABASE.md 9 节建议为准。 */
public interface AlertRepository {

    List<Alert> findAll();

    Optional<Alert> findById(String alertId);

    List<Alert> findByIds(Collection<String> alertIds);

    int count();
}

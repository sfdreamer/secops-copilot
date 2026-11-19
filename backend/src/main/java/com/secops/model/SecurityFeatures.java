package com.secops.model;

public record SecurityFeatures(int failedAttempts, int successfulAttempts, int distinctUserCount,
        long windowSeconds, double windowMinutes, double failureRate, boolean successAfterFailure,
        Boolean publicExposure, String assetCriticality) {}

package com.secops.model;

public record AssetProfile(String assetId, String assetName, String ipAddress,
        Boolean publicExposure, String assetCriticality, String owner) {}

package com.secops.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secops.model.AssetProfile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import java.io.IOException;
import java.util.*;

@Repository
public class AssetRepository {
    private final Map<String,AssetProfile> assets;
    public AssetRepository(ObjectMapper mapper) throws IOException {
        Map<String,AssetProfile> loaded=new HashMap<>();
        try (var stream=new ClassPathResource("mock/assets.json").getInputStream()) {
            List<AssetProfile> profiles=mapper.readValue(stream,new TypeReference<List<AssetProfile>>(){});
            for (AssetProfile p:profiles) {
                if (p==null || p.assetName()==null || p.assetName().isBlank()
                        || p.assetId()==null || p.assetId().isBlank()
                        || (p.assetCriticality()!=null && !Set.of("HIGH","MEDIUM","LOW","UNKNOWN").contains(p.assetCriticality()))
                        || loaded.putIfAbsent(p.assetName(),p)!=null)
                    throw new IllegalStateException("Invalid or duplicate asset fixture");
            }
        }
        assets=Map.copyOf(loaded);
    }
    public Optional<AssetProfile> findByName(String name) { return name==null?Optional.empty():Optional.ofNullable(assets.get(name)); }
}

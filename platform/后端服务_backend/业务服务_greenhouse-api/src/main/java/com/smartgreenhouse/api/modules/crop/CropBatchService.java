package com.smartgreenhouse.api.modules.crop;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CropBatchService {

    private final CropBatchMapper mapper;

    public CropBatchService(CropBatchMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CropBatchView> list() {
        return mapper.findAll().stream()
                .map(batch -> new CropBatchView(
                        batch.batchId(),
                        batch.cropName(),
                        batch.varietyName(),
                        batch.plantingDate(),
                        batch.expectedHarvestDate(),
                        batch.status(),
                        batch.greenhouseCode(),
                        batch.greenhouseName()
                ))
                .toList();
    }
}

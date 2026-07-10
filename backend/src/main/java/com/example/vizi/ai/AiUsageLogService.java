package com.example.vizi.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AiUsageLogService {

    private final AiUsageLogRepository repository;

    AiUsageLogService(AiUsageLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(String feature, String model, String status, long latencyMs, String errorCode) {
        repository.save(new AiUsageLog(feature, model, status, latencyMs, errorCode));
    }
}

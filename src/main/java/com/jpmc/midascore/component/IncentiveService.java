package com.jpmc.midascore.component;

import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class IncentiveService {

    private static final Logger logger = LoggerFactory.getLogger(IncentiveService.class);
    private static final String INCENTIVE_URL = "http://localhost:8080/incentive";

    private final RestTemplate restTemplate;

    public IncentiveService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public float getIncentive(Transaction transaction) {
        try {
            Incentive incentive = restTemplate.postForObject(INCENTIVE_URL, transaction, Incentive.class);
            if (incentive != null) {
                logger.debug("Incentive for transaction {}: {}", transaction, incentive.getAmount());
                return incentive.getAmount();
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch incentive: {}", e.getMessage());
        }
        return 0f;
    }
}

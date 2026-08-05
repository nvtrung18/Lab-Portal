package com.web.labportalbackend.ai.service;

public record AiQuotaDecision(boolean allowed, AiQuotaDenialReason denialReason) {

    public AiQuotaDecision {
        if (allowed == (denialReason != null)) {
            throw new IllegalArgumentException("allowed decisions have no denial reason and denied decisions require one");
        }
    }

    public static AiQuotaDecision allow() {
        return new AiQuotaDecision(true, null);
    }

    public static AiQuotaDecision deny(AiQuotaDenialReason reason) {
        return new AiQuotaDecision(false, reason);
    }
}

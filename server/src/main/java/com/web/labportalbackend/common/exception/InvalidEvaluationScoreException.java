package com.web.labportalbackend.common.exception;

import java.math.BigDecimal;

public class InvalidEvaluationScoreException extends RuntimeException {

    public InvalidEvaluationScoreException(BigDecimal score) {
        super("Evaluation score must be between 0.0 and 10.0: " + score);
    }

    public InvalidEvaluationScoreException(BigDecimal score, BigDecimal maxScore) {
        super("Evaluation score must be between 0.0 and " + maxScore + ": " + score);
    }
}

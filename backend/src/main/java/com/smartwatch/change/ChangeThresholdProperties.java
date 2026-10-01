package com.smartwatch.change;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.changes")
public class ChangeThresholdProperties {

    /**
     * Minimum absolute percent move versus the reference price.
     */
    private BigDecimal priceMovePercent = new BigDecimal("2");

    /**
     * Current volume must be at least this multiple of the reference volume.
     */
    private BigDecimal volumeSpikeMultiple = new BigDecimal("2");

    /**
     * Minimum absolute percent gap between the open and the previous close.
     */
    private BigDecimal gapPercent = new BigDecimal("1");

    /**
     * Minimum absolute percent move from the session open.
     */
    private BigDecimal intradayMovePercent = new BigDecimal("2");

    /**
     * A move at least this many times the threshold is high severity.
     */
    private BigDecimal highSeverityMultiple = new BigDecimal("2");

    public BigDecimal getPriceMovePercent() {
        return priceMovePercent;
    }

    public void setPriceMovePercent(BigDecimal priceMovePercent) {
        this.priceMovePercent = priceMovePercent;
    }

    public BigDecimal getVolumeSpikeMultiple() {
        return volumeSpikeMultiple;
    }

    public void setVolumeSpikeMultiple(BigDecimal volumeSpikeMultiple) {
        this.volumeSpikeMultiple = volumeSpikeMultiple;
    }

    public BigDecimal getGapPercent() {
        return gapPercent;
    }

    public void setGapPercent(BigDecimal gapPercent) {
        this.gapPercent = gapPercent;
    }

    public BigDecimal getIntradayMovePercent() {
        return intradayMovePercent;
    }

    public void setIntradayMovePercent(BigDecimal intradayMovePercent) {
        this.intradayMovePercent = intradayMovePercent;
    }

    public BigDecimal getHighSeverityMultiple() {
        return highSeverityMultiple;
    }

    public void setHighSeverityMultiple(BigDecimal highSeverityMultiple) {
        this.highSeverityMultiple = highSeverityMultiple;
    }
}

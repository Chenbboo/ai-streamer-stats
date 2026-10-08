package com.ruoyi.business.ai;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, source-addressable business fact exposed to an AI response layer.
 */
public final class BusinessAiEvidence implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String evidenceId;
    private final BusinessAiEvidenceEntityType entityType;
    private final String entityId;
    private final String entityName;
    private final String metricCode;
    private final String metricLabel;
    private final String value;
    private final String unit;
    private final String period;
    private final String sourcePath;
    private final Instant cutoffTime;
    private final BusinessAiEvidenceStatus status;

    private BusinessAiEvidence(Builder builder)
    {
        entityType = Objects.requireNonNull(builder.entityType, "entityType must not be null");
        entityId = requireText(builder.entityId, "entityId");
        entityName = requireText(builder.entityName, "entityName");
        metricCode = requireText(builder.metricCode, "metricCode");
        metricLabel = requireText(builder.metricLabel, "metricLabel");
        value = requireText(builder.value, "value");
        unit = normalizeOptionalText(builder.unit);
        period = requireText(builder.period, "period");
        sourcePath = requireText(builder.sourcePath, "sourcePath");
        cutoffTime = Objects.requireNonNull(builder.cutoffTime, "cutoffTime must not be null");
        status = Objects.requireNonNull(builder.status, "status must not be null");
        evidenceId = builder.evidenceId == null
                ? BusinessAiEvidenceIds.deterministic(entityType, entityId, metricCode, period, sourcePath)
                : requireText(builder.evidenceId, "evidenceId");
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public String getEvidenceId()
    {
        return evidenceId;
    }

    public BusinessAiEvidenceEntityType getEntityType()
    {
        return entityType;
    }

    public String getEntityId()
    {
        return entityId;
    }

    public String getEntityName()
    {
        return entityName;
    }

    public String getMetricCode()
    {
        return metricCode;
    }

    public String getMetricLabel()
    {
        return metricLabel;
    }

    public String getValue()
    {
        return value;
    }

    public String getUnit()
    {
        return unit;
    }

    public String getPeriod()
    {
        return period;
    }

    public String getSourcePath()
    {
        return sourcePath;
    }

    public Instant getCutoffTime()
    {
        return cutoffTime;
    }

    public BusinessAiEvidenceStatus getStatus()
    {
        return status;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof BusinessAiEvidence))
        {
            return false;
        }
        BusinessAiEvidence that = (BusinessAiEvidence) other;
        return evidenceId.equals(that.evidenceId)
                && entityType == that.entityType
                && entityId.equals(that.entityId)
                && entityName.equals(that.entityName)
                && metricCode.equals(that.metricCode)
                && metricLabel.equals(that.metricLabel)
                && value.equals(that.value)
                && unit.equals(that.unit)
                && period.equals(that.period)
                && sourcePath.equals(that.sourcePath)
                && cutoffTime.equals(that.cutoffTime)
                && status == that.status;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(evidenceId, entityType, entityId, entityName, metricCode, metricLabel,
                value, unit, period, sourcePath, cutoffTime, status);
    }

    @Override
    public String toString()
    {
        return "BusinessAiEvidence{" + "evidenceId='" + evidenceId + '\''
                + ", entityType=" + entityType + ", entityId='" + entityId + '\''
                + ", metricCode='" + metricCode + '\'' + ", period='" + period + '\''
                + ", status=" + status + '}';
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value)
    {
        return value == null ? "" : value.trim();
    }

    public static final class Builder
    {
        private String evidenceId;
        private BusinessAiEvidenceEntityType entityType;
        private String entityId;
        private String entityName;
        private String metricCode;
        private String metricLabel;
        private String value;
        private String unit;
        private String period;
        private String sourcePath;
        private Instant cutoffTime;
        private BusinessAiEvidenceStatus status;

        private Builder()
        {
        }

        public Builder evidenceId(String evidenceId)
        {
            this.evidenceId = evidenceId;
            return this;
        }

        public Builder entityType(BusinessAiEvidenceEntityType entityType)
        {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId)
        {
            this.entityId = entityId;
            return this;
        }

        public Builder entityName(String entityName)
        {
            this.entityName = entityName;
            return this;
        }

        public Builder metricCode(String metricCode)
        {
            this.metricCode = metricCode;
            return this;
        }

        public Builder metricLabel(String metricLabel)
        {
            this.metricLabel = metricLabel;
            return this;
        }

        public Builder value(String value)
        {
            this.value = value;
            return this;
        }

        public Builder unit(String unit)
        {
            this.unit = unit;
            return this;
        }

        public Builder period(String period)
        {
            this.period = period;
            return this;
        }

        public Builder sourcePath(String sourcePath)
        {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder cutoffTime(Instant cutoffTime)
        {
            this.cutoffTime = cutoffTime;
            return this;
        }

        public Builder status(BusinessAiEvidenceStatus status)
        {
            this.status = status;
            return this;
        }

        public BusinessAiEvidence build()
        {
            return new BusinessAiEvidence(this);
        }
    }
}

package com.ruoyi.business.ai;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable evidence result returned by a read-only business query.
 */
public final class BusinessAiEvidenceBundle implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String scope;
    private final Instant asOf;
    private final BusinessAiEvidenceCoverage coverage;
    private final List<BusinessAiEvidence> evidence;
    private final List<String> warnings;

    private BusinessAiEvidenceBundle(Builder builder)
    {
        scope = requireText(builder.scope, "scope");
        asOf = Objects.requireNonNull(builder.asOf, "asOf must not be null");
        coverage = Objects.requireNonNull(builder.coverage, "coverage must not be null");
        evidence = Collections.unmodifiableList(new ArrayList<>(builder.evidenceById.values()));
        warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public String getScope()
    {
        return scope;
    }

    public Instant getAsOf()
    {
        return asOf;
    }

    public BusinessAiEvidenceCoverage getCoverage()
    {
        return coverage;
    }

    public List<BusinessAiEvidence> getEvidence()
    {
        return evidence;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    public boolean hasFullCoverage()
    {
        return coverage == BusinessAiEvidenceCoverage.FULL;
    }

    public Optional<BusinessAiEvidence> findEvidence(String evidenceId)
    {
        if (evidenceId == null)
        {
            return Optional.empty();
        }
        for (BusinessAiEvidence item : evidence)
        {
            if (item.getEvidenceId().equals(evidenceId))
            {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof BusinessAiEvidenceBundle))
        {
            return false;
        }
        BusinessAiEvidenceBundle that = (BusinessAiEvidenceBundle) other;
        return scope.equals(that.scope)
                && asOf.equals(that.asOf)
                && coverage == that.coverage
                && evidence.equals(that.evidence)
                && warnings.equals(that.warnings);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(scope, asOf, coverage, evidence, warnings);
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public static final class Builder
    {
        private String scope;
        private Instant asOf;
        private BusinessAiEvidenceCoverage coverage;
        private final Map<String, BusinessAiEvidence> evidenceById = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();

        private Builder()
        {
        }

        public Builder scope(String scope)
        {
            this.scope = scope;
            return this;
        }

        public Builder asOf(Instant asOf)
        {
            this.asOf = asOf;
            return this;
        }

        public Builder coverage(BusinessAiEvidenceCoverage coverage)
        {
            this.coverage = coverage;
            return this;
        }

        public Builder addEvidence(BusinessAiEvidence item)
        {
            Objects.requireNonNull(item, "evidence must not be null");
            BusinessAiEvidence existing = evidenceById.putIfAbsent(item.getEvidenceId(), item);
            if (existing != null)
            {
                throw new IllegalArgumentException("duplicate evidenceId: " + item.getEvidenceId());
            }
            return this;
        }

        public Builder evidence(Collection<BusinessAiEvidence> items)
        {
            Objects.requireNonNull(items, "evidence collection must not be null");
            for (BusinessAiEvidence item : items)
            {
                addEvidence(item);
            }
            return this;
        }

        public Builder addWarning(String warning)
        {
            warnings.add(requireText(warning, "warning"));
            return this;
        }

        public Builder warnings(Collection<String> items)
        {
            Objects.requireNonNull(items, "warnings collection must not be null");
            for (String item : items)
            {
                addWarning(item);
            }
            return this;
        }

        public BusinessAiEvidenceBundle build()
        {
            return new BusinessAiEvidenceBundle(this);
        }
    }
}

package com.ruoyi.business.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessAiEvidenceBundleTest
{
    private static final Instant CUTOFF = Instant.parse("2026-08-13T08:00:00Z");

    @Test
    void generatesStableUniqueEvidenceIdsFromFactIdentity()
    {
        BusinessAiEvidence first = evidence("routine.today.actual", "10");
        BusinessAiEvidence sameIdentity = evidence("routine.today.actual", "11");
        BusinessAiEvidence differentMetric = evidence("routine.today.target", "15");

        assertEquals(first.getEvidenceId(), sameIdentity.getEvidenceId());
        assertNotEquals(first.getEvidenceId(), differentMetric.getEvidenceId());
        assertTrue(first.getEvidenceId().matches("evi_[0-9a-f]{64}"));
    }

    @Test
    void rejectsDuplicateEvidenceIdsInsideOneBundle()
    {
        BusinessAiEvidence first = evidence("routine.today.actual", "10");
        BusinessAiEvidence duplicateIdentity = evidence("routine.today.actual", "11");

        BusinessAiEvidenceBundle.Builder builder = BusinessAiEvidenceBundle.builder()
                .scope("project:13/member-progress")
                .asOf(CUTOFF)
                .coverage(BusinessAiEvidenceCoverage.FULL)
                .addEvidence(first);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> builder.addEvidence(duplicateIdentity));
        assertTrue(exception.getMessage().contains(first.getEvidenceId()));
    }

    @Test
    void defensivelyCopiesCollectionsAndExposesReadOnlyViews()
    {
        List<BusinessAiEvidence> sourceEvidence = new ArrayList<>();
        sourceEvidence.add(evidence("routine.today.actual", "10"));
        List<String> sourceWarnings = new ArrayList<>(Arrays.asList("one member has not reported"));

        BusinessAiEvidenceBundle bundle = BusinessAiEvidenceBundle.builder()
                .scope("project:13/member-progress")
                .asOf(CUTOFF)
                .coverage(BusinessAiEvidenceCoverage.PARTIAL)
                .evidence(sourceEvidence)
                .warnings(sourceWarnings)
                .build();

        sourceEvidence.clear();
        sourceWarnings.clear();

        assertEquals(1, bundle.getEvidence().size());
        assertEquals(1, bundle.getWarnings().size());
        assertThrows(UnsupportedOperationException.class, () -> bundle.getEvidence().clear());
        assertThrows(UnsupportedOperationException.class, () -> bundle.getWarnings().add("changed"));
    }

    @Test
    void remainsEqualAfterJavaSerializationRoundTrip() throws Exception
    {
        BusinessAiEvidenceBundle original = BusinessAiEvidenceBundle.builder()
                .scope("project:13/member-progress")
                .asOf(CUTOFF)
                .coverage(BusinessAiEvidenceCoverage.FULL)
                .addEvidence(evidence("routine.today.actual", "10"))
                .build();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes))
        {
            output.writeObject(original);
        }

        BusinessAiEvidenceBundle restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
        {
            restored = (BusinessAiEvidenceBundle) input.readObject();
        }

        assertEquals(original, restored);
        assertTrue(restored.findEvidence(original.getEvidence().get(0).getEvidenceId()).isPresent());
        assertThrows(UnsupportedOperationException.class, () -> restored.getEvidence().clear());
    }

    @Test
    void rejectsIncompleteEvidenceInsteadOfPublishingAmbiguousFacts()
    {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> BusinessAiEvidence.builder()
                        .entityType(BusinessAiEvidenceEntityType.PERSON)
                        .entityId("100")
                        .entityName("Stone")
                        .metricCode(" ")
                        .metricLabel("Today completed")
                        .value("10")
                        .unit("items")
                        .period("2026-08-13")
                        .sourcePath("business_project_routine#9.today_actual")
                        .cutoffTime(CUTOFF)
                        .status(BusinessAiEvidenceStatus.CONFIRMED)
                        .build());

        assertTrue(exception.getMessage().contains("metricCode"));
    }

    @Test
    void rejectsBlankEntityIdBeforeItCanBecomeUntraceableEvidence()
    {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> taskEvidence(" ", "Task without id", "20"));

        assertTrue(exception.getMessage().contains("entityId"));
    }

    @Test
    void repeatedFallbackTaskIdsCollapseAndMakeTheBundleFailClosed()
    {
        BusinessAiEvidence first = taskEvidence("unknown", "First task", "20");
        BusinessAiEvidence second = taskEvidence("unknown", "Second task", "80");

        // Names and values are deliberately excluded from identity: changing a display name
        // must not silently turn one source record into a new fact. Consequently a caller that
        // normalizes multiple missing task IDs to "unknown" creates a real collision.
        assertEquals(first.getEvidenceId(), second.getEvidenceId());

        BusinessAiEvidenceBundle.Builder builder = BusinessAiEvidenceBundle.builder()
                .scope("project:13/member-progress")
                .asOf(CUTOFF)
                .coverage(BusinessAiEvidenceCoverage.PARTIAL)
                .addEvidence(first);
        assertThrows(IllegalArgumentException.class, () -> builder.addEvidence(second));
    }

    @Test
    void repeatedTaskIdCannotBeDisambiguatedOnlyByMemberDisplayName()
    {
        BusinessAiEvidence stone = taskEvidence("task-9", "Stone task", "20");
        BusinessAiEvidence jiang = BusinessAiEvidence.builder()
                .entityType(BusinessAiEvidenceEntityType.TASK)
                .entityId("task-9")
                .entityName("Jiang task")
                .metricCode("task_progress")
                .metricLabel("Jiang progress")
                .value("80")
                .unit("%")
                .period("2026-08-13")
                .sourcePath("business_project_task.task_progress")
                .cutoffTime(CUTOFF)
                .status(BusinessAiEvidenceStatus.CONFIRMED)
                .build();

        assertEquals(stone.getEvidenceId(), jiang.getEvidenceId());
    }

    private BusinessAiEvidence evidence(String metricCode, String value)
    {
        return BusinessAiEvidence.builder()
                .entityType(BusinessAiEvidenceEntityType.PERSON)
                .entityId("100")
                .entityName("Stone")
                .metricCode(metricCode)
                .metricLabel(metricCode)
                .value(value)
                .unit("items")
                .period("2026-08-13")
                .sourcePath("business_project_routine#9." + metricCode)
                .cutoffTime(CUTOFF)
                .status(BusinessAiEvidenceStatus.CONFIRMED)
                .build();
    }

    private BusinessAiEvidence taskEvidence(String taskId, String taskName, String value)
    {
        return BusinessAiEvidence.builder()
                .entityType(BusinessAiEvidenceEntityType.TASK)
                .entityId(taskId)
                .entityName(taskName)
                .metricCode("task_progress")
                .metricLabel(taskName + " progress")
                .value(value)
                .unit("%")
                .period("2026-08-13")
                .sourcePath("business_project_task.task_progress")
                .cutoffTime(CUTOFF)
                .status(BusinessAiEvidenceStatus.CONFIRMED)
                .build();
    }
}

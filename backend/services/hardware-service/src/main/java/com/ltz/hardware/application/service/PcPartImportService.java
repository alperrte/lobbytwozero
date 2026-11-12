package com.ltz.hardware.application.service;

import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.infrastructure.client.PcPartDatasetReader.Dataset;
import com.ltz.hardware.infrastructure.client.PcPartDatasetReader.Issue;
import com.ltz.hardware.infrastructure.persistence.repository.PcPartImportRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PcPartImportService {
    public record Counts(int added, int updated, int unchanged, int skipped, int failed) { }
    public record Result(boolean dryRun, String revision, Map<String, Counts> categories, List<Issue> issues) { }
    private final PcPartImportRepository repository;
    private final TransactionTemplate transaction;

    public PcPartImportService(PcPartImportRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(60);
    }

    public Result run(Dataset dataset, int batchSize, boolean dryRun) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("INVALID_BATCH_SIZE");
        Instant started = Instant.now();
        var counts = new TreeMap<String, int[]>();
        for (var category : List.of(ComponentCategory.CPU, ComponentCategory.GPU, ComponentCategory.MOTHERBOARD,
                ComponentCategory.RAM, ComponentCategory.STORAGE)) {
            counts.put(category.name(), new int[5]);
        }
        var issues = new ArrayList<>(dataset.issues());
        var rows = dataset.products();
        for (int start = 0; start < rows.size(); start += batchSize) {
            var batch = rows.subList(start, Math.min(start + batchSize, rows.size()));
            try {
                var outcomes = importBatch(batch, dataset.revision(), dryRun);
                for (int index = 0; index < batch.size(); index++) counts.get(batch.get(index).category().name())[outcomes.get(index).ordinal()]++;
            } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException _) {
                // Entire batch rolled back. Later batches can proceed; never log SQL payload/exception text.
                for (var row : batch) counts.get(row.category().name())[4]++;
                issues.add(new Issue(batch.getFirst().category(), start + 1, "BATCH_ROLLED_BACK"));
            }
        }
        var categories = new TreeMap<String, Counts>();
        counts.forEach((key, value) -> categories.put(key, new Counts(value[0], value[1], value[2], value[3], value[4])));
        var result = new Result(dryRun, dataset.revision(), Map.copyOf(categories), List.copyOf(issues));
        boolean partial = !issues.isEmpty() || result.categories().values().stream().anyMatch(c -> c.skipped() > 0);
        if (!dryRun) transaction.executeWithoutResult(status -> repository.report(dataset.revision(), dataset.files(),
                result, partial, started));
        return result;
    }

    private List<PcPartImportRepository.Outcome> importBatch(
            List<com.ltz.hardware.domain.entity.PcPartProduct> batch, String revision, boolean dryRun) {
        return transaction.execute(status -> {
            if (!dryRun) repository.lock();
            var result = batch.stream().map(row -> repository.upsert(row, revision, dryRun)).toList();
            if (dryRun) status.setRollbackOnly();
            return result;
        });
    }
}

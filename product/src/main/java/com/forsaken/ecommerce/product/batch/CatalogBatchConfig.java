package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.model.Product;
import com.forsaken.ecommerce.product.repository.ICategoryRepository;
import com.forsaken.ecommerce.product.repository.IProductRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Catalog jobs. Export writes the catalog to a local file in keyset-paged chunks and then uploads it to
 * object storage. Import reads a CSV object from storage; bad rows are skipped and logged. The low-stock job
 * reports products below the configured threshold.
 */
@Slf4j
@Configuration
public class CatalogBatchConfig {

    public static final String EXPORT_JOB = "productExportJob";
    public static final String IMPORT_JOB = "productImportJob";
    public static final String LOW_STOCK_JOB = "lowStockAlertJob";

    public static final String PARAM_FILE = "file";
    public static final String PARAM_UPLOAD_KEY = "uploadKey";
    public static final String PARAM_KEY = "key";

    @Bean
    public Job productExportJob(
            final JobRepository jobRepository,
            final PlatformTransactionManager transactionManager,
            final IProductRepository productRepository,
            final ProductFileStorage storage,
            final CatalogBatchProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final FlatFileItemWriter<String> writer = new FlatFileItemWriter<>();
        writer.setName("productExportWriter");
        writer.setLineAggregator(new PassThroughLineAggregator<>());
        writer.setHeaderCallback(out -> out.write(ProductCsv.HEADER));

        final StepExecutionListener bindFile = new StepExecutionListener() {
            @Override
            public void beforeStep(final StepExecution step) {
                writer.setResource(new FileSystemResource(step.getJobParameters().getString(PARAM_FILE)));
            }
        };
        final Step export = new StepBuilder(EXPORT_JOB + "WriteStep", jobRepository)
                .<Product, String>chunk(properties.chunkSize(), transactionManager)
                .reader(new KeysetItemReader<>("export.last.read.id",
                        (afterId, size) -> productRepository.findPageAfterId(afterId, PageRequest.of(0, size)),
                        Product::getId, properties.chunkSize()))
                .processor(ProductCsv::format)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(properties.retryLimit())
                .listener(bindFile)
                .listener(new CatalogStepListener(EXPORT_JOB, meterRegistry))
                .build();

        final Tasklet upload = (contribution, chunkContext) -> {
            final var parameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
            final Path file = Path.of(parameters.getString(PARAM_FILE));
            storage.upload(parameters.getString(PARAM_UPLOAD_KEY), file);
            Files.deleteIfExists(file);
            return RepeatStatus.FINISHED;
        };
        final Step uploadStep = new StepBuilder(EXPORT_JOB + "UploadStep", jobRepository)
                .tasklet(upload, transactionManager)
                .build();
        return new JobBuilder(EXPORT_JOB, jobRepository).start(export).next(uploadStep).build();
    }

    @Bean
    public Job productImportJob(
            final JobRepository jobRepository,
            final PlatformTransactionManager transactionManager,
            final IProductRepository productRepository,
            final ICategoryRepository categoryRepository,
            final ProductFileStorage storage,
            final CatalogBatchProperties properties,
            final MeterRegistry meterRegistry,
            final CacheManager cacheManager
    ) {
        final DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames(ProductCsv.COLUMNS);
        final DefaultLineMapper<ProductCsvRow> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fields -> new ProductCsvRow(
                fields.readString("id"), fields.readString("name"), fields.readString("description"),
                fields.readString("availableQuantity"), fields.readString("price"),
                fields.readString("categoryId"), fields.readString("imageUrl")));
        final FlatFileItemReader<ProductCsvRow> reader = new FlatFileItemReader<>();
        reader.setName("productImportReader");
        reader.setLinesToSkip(1);
        reader.setLineMapper(lineMapper);

        final StepExecutionListener bindKeyAndEvictCaches = new StepExecutionListener() {
            @Override
            public void beforeStep(final StepExecution step) {
                reader.setResource(new S3ObjectResource(storage, step.getJobParameters().getString(PARAM_KEY)));
            }

            @Override
            public ExitStatus afterStep(final StepExecution step) {
                for (final String name : new String[]{"products", "productById"}) {
                    final Cache cache = cacheManager.getCache(name);
                    if (cache != null) {
                        cache.clear();
                    }
                }
                return step.getExitStatus();
            }
        };
        final ItemWriter<Product> writer = chunk -> productRepository.saveAll(chunk.getItems());
        final Step step = new StepBuilder(IMPORT_JOB + "Step", jobRepository)
                .<ProductCsvRow, Product>chunk(properties.chunkSize(), transactionManager)
                .reader(reader)
                .processor(new ProductImportProcessor(productRepository, categoryRepository))
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(properties.retryLimit())
                .skip(ProductImportException.class)
                .skip(FlatFileParseException.class)
                .skipLimit(properties.skipLimit())
                .listener(bindKeyAndEvictCaches)
                .listener(new CatalogStepListener(IMPORT_JOB, meterRegistry))
                .listener(new CatalogSkipListener(IMPORT_JOB))
                .build();
        return new JobBuilder(IMPORT_JOB, jobRepository).start(step).build();
    }

    @Bean
    public Job lowStockAlertJob(
            final JobRepository jobRepository,
            final PlatformTransactionManager transactionManager,
            final IProductRepository productRepository,
            final CatalogBatchProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final ItemWriter<Product> writer = chunk -> chunk.getItems().forEach(product -> {
            log.warn("Low stock: productId={} name={} availableQuantity={} threshold={}",
                    product.getId(), product.getName(), product.getAvailableQuantity(),
                    properties.lowStockThreshold());
            meterRegistry.counter("product.low_stock.alerts").increment();
        });
        final Step step = new StepBuilder(LOW_STOCK_JOB + "Step", jobRepository)
                .<Product, Product>chunk(properties.chunkSize(), transactionManager)
                .reader(new KeysetItemReader<>("lowstock.last.read.id",
                        (afterId, size) -> productRepository.findLowStockPageAfterId(
                                properties.lowStockThreshold(), afterId, PageRequest.of(0, size)),
                        Product::getId, properties.chunkSize()))
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(properties.retryLimit())
                .listener(new CatalogStepListener(LOW_STOCK_JOB, meterRegistry))
                .build();
        return new JobBuilder(LOW_STOCK_JOB, jobRepository).start(step).build();
    }
}

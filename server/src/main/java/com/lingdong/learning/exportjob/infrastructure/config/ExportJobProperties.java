package com.lingdong.learning.exportjob.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** 异步导出调度、分页和本地临时文件配置。 */
@ConfigurationProperties(prefix = "lingdong.export-job")
public class ExportJobProperties {
    private boolean schedulingEnabled = true;
    private int batchSize = 20;
    private int queryPageSize = 500;
    private int sheetMaxRows = 50_000;
    private String cron = "0 */1 * * * *";
    private Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"), "lingdong-export-jobs");
    private String sourceHmacSecret;

    public boolean isSchedulingEnabled() { return schedulingEnabled; }
    public void setSchedulingEnabled(boolean schedulingEnabled) { this.schedulingEnabled = schedulingEnabled; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getQueryPageSize() { return queryPageSize; }
    public void setQueryPageSize(int queryPageSize) { this.queryPageSize = queryPageSize; }
    public int getSheetMaxRows() { return sheetMaxRows; }
    public void setSheetMaxRows(int sheetMaxRows) { this.sheetMaxRows = sheetMaxRows; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public Path getTempDirectory() { return tempDirectory; }
    public void setTempDirectory(Path tempDirectory) { this.tempDirectory = tempDirectory; }
    public String getSourceHmacSecret() { return sourceHmacSecret; }
    public void setSourceHmacSecret(String sourceHmacSecret) { this.sourceHmacSecret = sourceHmacSecret; }
}

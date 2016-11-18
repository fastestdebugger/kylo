package com.thinkbiganalytics.nifi.v2.sqoop.utils;

import com.thinkbiganalytics.nifi.v2.sqoop.process.SqoopProcessResult;

import org.apache.nifi.logging.ComponentLog;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Helpful methods to use with Sqoop processors
 * @author jagrut sharma
 *
 */
public class SqoopUtils {

    private final static String NO_NEW_ROWS_IDENTIFIER = "No new rows detected since last import";

    /**
     * Get credentials/parameters for which values must be masked
     * @return credentials/parameters map.<br>Key = credential, Value = delimiter between credential and value
     */
    public Map<String, String> getCredentialsToMask() {
        final String EQUAL_STRING = "=";
        final String SPACE_STRING = " ";
        final String PASSPHRASE_IDENTIFIER="-Dorg.apache.sqoop.credentials.loader.crypto.passphrase";
        final String PASSWORD_FILE_IDENTIFIER="--password-file";
        final String PASSWORD_IDENTIFIER = "--password";

        Map<String, String> credentialsToMask = new HashMap<>();
        credentialsToMask.put(PASSPHRASE_IDENTIFIER, EQUAL_STRING);
        credentialsToMask.put(PASSWORD_FILE_IDENTIFIER, SPACE_STRING);
        credentialsToMask.put(PASSWORD_IDENTIFIER, SPACE_STRING);

        return credentialsToMask;
    }

    /**
     * Mask credentials in sqoop command
     * @param sqoopCommand sqoop command executed
     * @param credentialIdentifiers map of credentials for which values must be masked. <br>Key = credential, Value = delimiter between credential and value
     * @return sqoop command with credentials masked
     */
    public String maskCredentials(String sqoopCommand, Map<String, String> credentialIdentifiers) {
        final String MASK = "*****";
        for (Map.Entry<String, String> credentialIdentifier: credentialIdentifiers.entrySet()) {
            if (!sqoopCommand.contains(credentialIdentifier.getKey())) {
                continue;
            }
            int startPosCredentialIdentifier = sqoopCommand.indexOf(credentialIdentifier.getKey());
            int startPosValue = sqoopCommand.indexOf(credentialIdentifier.getValue(), startPosCredentialIdentifier);
            int endPosValue = sqoopCommand.indexOf(" ", startPosValue + 1);
            sqoopCommand = sqoopCommand.substring(0, startPosValue + 1) + MASK + sqoopCommand.substring(endPosValue, sqoopCommand.length());
        }
        return sqoopCommand;
    }

    /**
     * Get count of records extracted
     * @param sqoopProcessResult {@link SqoopProcessResult}
     * @param logger Logger
     * @return extraction record count
     */
    public int getSqoopRecordCount(SqoopProcessResult sqoopProcessResult, ComponentLog logger) {
        String[] logLines = sqoopProcessResult.getLogLines();

        if ((sqoopProcessResult.getExitValue() != 0) || (logLines[0] == null)) {
            logger.info("Skipping attempt to retrieve number of records extracted");
            return -1;
        }

        //Example of logLines[0]:
        //16/10/12 21:50:03 INFO mapreduce.ImportJobBase: Retrieved 2 records.
        //16/10/21 02:05:41 INFO tool.ImportTool: No new rows detected since last import.
        String recordCountLogLine = logLines[0];
        if (recordCountLogLine.contains(NO_NEW_ROWS_IDENTIFIER)) {
            return 0;
        }

        final String START_RECORD_COUNT_IDENTIFIER = "Retrieved";
        final String END_RECORD_COUNT_IDENTIFIER = "records.";
        int start = recordCountLogLine.indexOf(START_RECORD_COUNT_IDENTIFIER);
        int end = recordCountLogLine.indexOf(END_RECORD_COUNT_IDENTIFIER);
        String numberString = recordCountLogLine.substring(start + START_RECORD_COUNT_IDENTIFIER.length(), end).trim();
        try {
            return Integer.parseInt(numberString);
        }
        catch (Exception e) {
            logger.info("Unable to parse number of records extracted. " + e.getMessage());
            return -1;
        }
    }

    /**
     * Get count of records exported (for sqoop export job)
     * @param sqoopExportProcessResult {@link SqoopProcessResult}
     * @param logger Logger
     * @return export record count
     */
    public int getSqoopExportRecordCount(SqoopProcessResult sqoopExportProcessResult, ComponentLog logger) {
        String[] logLines = sqoopExportProcessResult.getLogLines();

        if ((sqoopExportProcessResult.getExitValue() != 0) || (logLines[0] == null)) {
            logger.info("Skipping attempt to retrieve number of records exported");
            return -1;
        }

        //Example of logLines[0]:
        //16/11/17 00:25:14 INFO mapreduce.ExportJobBase: Exported 4 records.
        //In case of no records to export, the above will report: Exported 0 records.
        String recordExportCountLogLine = logLines[0];

        final String START_EXPORT_RECORD_COUNT_IDENTIFIER = "Exported";
        final String END_EXPORT_RECORD_COUNT_IDENTIFIER = "records.";
        int start = recordExportCountLogLine.indexOf(START_EXPORT_RECORD_COUNT_IDENTIFIER);
        int end = recordExportCountLogLine.indexOf(END_EXPORT_RECORD_COUNT_IDENTIFIER);
        String numberString = recordExportCountLogLine.substring(start + START_EXPORT_RECORD_COUNT_IDENTIFIER.length(), end).trim();
        try {
            return Integer.parseInt(numberString);
        }
        catch (Exception e) {
            logger.info("Unable to parse number of records exported. " + e.getMessage());
            return -1;
        }
    }

    /**
     * Get next high watermark value for incremental load
     * @param sqoopProcessResult {@link SqoopProcessResult}
     * @return new high watermark value
     */
    public String getNewHighWatermark(SqoopProcessResult sqoopProcessResult) {
        String[] logLines = sqoopProcessResult.getLogLines();

        final String NO_UPDATE = "NO_UPDATE";
        final String LAST_VALUE_IDENTIFIER = "--last-value";

        if ((sqoopProcessResult.getExitValue() != 0) || (logLines.length <= 1)) {
            return NO_UPDATE;
        }
        else if ((logLines[0] != null) && (logLines[0].contains(NO_NEW_ROWS_IDENTIFIER))) {
            return NO_UPDATE;
        }
        else {
            if (logLines[1] == null) return NO_UPDATE;
            //16/10/18 23:37:11 INFO tool.ImportTool:   --last-value 1006
            String newHighWaterMarkLogLine = logLines[1];
            int end = newHighWaterMarkLogLine.length();
            int start = newHighWaterMarkLogLine.indexOf(LAST_VALUE_IDENTIFIER);
            return newHighWaterMarkLogLine.substring(start + LAST_VALUE_IDENTIFIER.length(), end).trim();
        }
    }

    /**
     * Check if source relational system is Teradata
     * @param driver Driver for source relational system
     * @return true/false indicating if source system is Teradata
     */
    public Boolean isTeradataDatabase(@Nonnull String driver) {
        final String TERADATA_IDENTIFIER = "teradata";
        return driver.toLowerCase().contains(TERADATA_IDENTIFIER);
    }

    /**
     * Check if source relational system is Oracle
     * @param driver Driver for source relational system
     * @return true/false indicating if source system is Teradata
     */
    public Boolean isOracleDatabase(@Nonnull String driver) {
        final String ORACLE_IDENTIFIER = "oracle";
        return driver.toLowerCase().contains(ORACLE_IDENTIFIER);
    }
}

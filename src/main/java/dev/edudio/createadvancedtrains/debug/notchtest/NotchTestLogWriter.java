package dev.edudio.createadvancedtrains.debug.notchtest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import dev.edudio.createadvancedtrains.control.notch.Notch;

/**
 * Dedicated Phase 5A control-test writer. It is independent of TrainDataDebugger.
 */
final class NotchTestLogWriter {

    private static final int SCHEMA_VERSION = 4;
    private static final int FLUSH_INTERVAL_SAMPLES = 20;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("uuuuMMdd'T'HHmmss.SSSX")
            .withZone(ZoneOffset.UTC);

    private final UUID trainId;
    private final UUID testSessionId;
    private final Path path;
    private final BufferedWriter writer;

    private boolean closed;
    private int samplesSinceFlush;

    /**
     * このクラスのインスタンスを初期化します。
     * @param trainId 対象列車を識別するUUID。
     * @param testSessionId 仕様書に個別説明がないため、{@code testSessionId}が示す対象識別子。
     * @param path 処理対象のファイルパス。
     * @param writer 出力先のログライター。
     */
    private NotchTestLogWriter(
            UUID trainId,
            UUID testSessionId,
            Path path,
            BufferedWriter writer) {
        this.trainId = trainId;
        this.testSessionId = testSessionId;
        this.path = path;
        this.writer = writer;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code open}としてまとめられている処理を実行します。
     * @param outputDirectory ログファイルを作成する出力ディレクトリ。
     * @param trainId 対象列車を識別するUUID。
     * @param testSessionId 仕様書に個別説明がないため、{@code testSessionId}が示す対象識別子。
     * @param sessionStartedAtUtc 仕様書に個別説明がないため、現在の処理内容から推定した、{@code sessionStartedAtUtc}として使用される入力値。
     * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
     * @param holdStopTargetUntilStop 仕様書に個別説明がないため、{@code holdStopTargetUntilStop}が示す条件の有効・無効を表す値。
     * @return 処理によって得られた結果。
     */
    static NotchTestLogWriter open(
            Path outputDirectory,
            UUID trainId,
            UUID testSessionId,
            Instant sessionStartedAtUtc,
            Notch fixedNotch,
            boolean holdStopTargetUntilStop) throws IOException {
        Files.createDirectories(outputDirectory);
        Path path = createUniquePath(outputDirectory, trainId, sessionStartedAtUtc);
        BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        NotchTestLogWriter logWriter = new NotchTestLogWriter(
                trainId,
                testSessionId,
                path,
                writer);
        logWriter.writeSessionStart(
                sessionStartedAtUtc,
                fixedNotch,
                holdStopTargetUntilStop);
        return logWriter;
    }

    /**
     * 現在のPathを返します。
     * @return 処理によって得られた結果。
     */
    Path getPath() {
        return path;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code writeSample}が示すデータを出力先へ書き込みます。
     * @param snapshot 記録または判定に使用する不変スナップショット。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    boolean writeSample(NotchTestSnapshot snapshot) {
        if (closed) {
            return false;
        }

        try {
            JsonObject record = baseRecord("sample");
            record.addProperty("serverTick", snapshot.serverTick());
            record.addProperty("selectionMode", "FIXED_FOR_TEST");
            record.addProperty("fixedTestNotch", snapshot.fixedTestNotch().name());
            record.addProperty("commandedNotch", snapshot.commandedNotch().name());
            record.addProperty("appliedNotch", snapshot.appliedNotch().name());
            record.addProperty("transitionElapsedTicks", snapshot.transitionElapsedTicks());
            record.addProperty("transitionProgress", snapshot.transitionProgress());
            addNullableNumber(
                    record,
                    "currentSpeedBlocksPerSecond",
                    snapshot.currentSpeedBlocksPerSecond());
            addNullableNumber(
                    record,
                    "preCatTargetSpeedBlocksPerSecond",
                    snapshot.preCatTargetSpeedBlocksPerSecond());
            addNullableNumber(
                    record,
                    "finalTargetSpeedBlocksPerSecond",
                    snapshot.finalTargetSpeedBlocksPerSecond());
            addNullableNumber(
                    record,
                    "baseAccelerationBlocksPerSecondSquared",
                    snapshot.baseAccelerationBlocksPerSecondSquared());
            addNullableNumber(
                    record,
                    "profileTargetAccelerationBlocksPerSecondSquared",
                    snapshot.profileTargetAccelerationBlocksPerSecondSquared());
            record.addProperty(
                    "effectiveAccelerationBlocksPerSecondSquared",
                    snapshot.effectiveAccelerationBlocksPerSecondSquared());
            addNullableNumber(
                    record,
                    "measuredAccelerationBlocksPerSecondSquared",
                    snapshot.measuredAccelerationBlocksPerSecondSquared());
            addNullableNumber(
                    record,
                    "signedVelocityAccelerationBlocksPerSecondSquared",
                    snapshot.signedVelocityAccelerationBlocksPerSecondSquared());
            record.addProperty("applicationState", snapshot.applicationState());
            addNullableString(record, "applicationReason", snapshot.applicationReason());
            record.addProperty("controlApplied", snapshot.controlApplied());
            addNullableNumber(record, "originalAccelerationMod", snapshot.originalAccelerationMod());
            addNullableNumber(record, "appliedAccelerationMod", snapshot.appliedAccelerationMod());
            record.addProperty("approachCallCount", snapshot.approachCallCount());
            record.addProperty("brakingDemandCallCount", snapshot.brakingDemandCallCount());
            record.addProperty(
                    "notchModifierAppliedCallCount",
                    snapshot.notchModifierAppliedCallCount());

            JsonArray approachCalls = new JsonArray();
            for (NotchTestApproachCall call : snapshot.approachCalls()) {
                JsonObject callRecord = new JsonObject();
                callRecord.addProperty("sequenceInServerTick", call.sequenceInServerTick());
                addNullableNumber(
                        callRecord,
                        "preCatTargetSpeedBlocksPerSecond",
                        call.preCatTargetSpeedBlocksPerSecond());
                addNullableNumber(
                        callRecord,
                        "nativeTargetSpeedBlocksPerSecond",
                        call.nativeTargetSpeedBlocksPerSecond());
                addNullableNumber(
                        callRecord,
                        "finalTargetSpeedBlocksPerSecond",
                        call.finalTargetSpeedBlocksPerSecond());
                callRecord.addProperty("brakingDemand", call.brakingDemand());
                addNullableString(callRecord, "applicationReason", call.applicationReason());
                addNullableNumber(
                        callRecord,
                        "originalAccelerationMod",
                        call.originalAccelerationMod());
                addNullableNumber(
                        callRecord,
                        "returnedAccelerationMod",
                        call.returnedAccelerationMod());
                callRecord.addProperty("notchModifierApplied", call.notchModifierApplied());
                callRecord.addProperty("stopTargetHoldState", call.stopTargetHoldState().name());
                callRecord.addProperty(
                        "stopTargetHoldOverrideApplied",
                        call.stopTargetHoldOverrideApplied());
                addNullableString(
                        callRecord,
                        "stopTargetHoldTriggerReason",
                        call.stopTargetHoldTriggerReason());
                approachCalls.add(callRecord);
            }
            record.add("approachCalls", approachCalls);

            NotchTestResponseState responseState = snapshot.responseState();
            JsonObject responseStateRecord = new JsonObject();
            responseStateRecord.addProperty(
                    "storedEffectiveAccelerationBlocksPerSecondSquared",
                    responseState.storedEffectiveAccelerationBlocksPerSecondSquared());
            addNullableNumber(
                    responseStateRecord,
                    "activeEffectiveAccelerationBlocksPerSecondSquared",
                    responseState.activeEffectiveAccelerationBlocksPerSecondSquared());
            responseStateRecord.addProperty(
                    "transitionElapsedTicks",
                    responseState.transitionElapsedTicks());
            responseStateRecord.addProperty(
                    "transitionTimebase",
                    responseState.transitionTimebase());
            record.add("responseState", responseStateRecord);

            JsonObject navigation = new JsonObject();
            addNullableNumber(
                    navigation,
                    "distanceToDestinationBlocks",
                    snapshot.distanceToDestinationBlocks());
            record.add("navigation", navigation);

            writeLine(record);
            samplesSinceFlush++;
            if (samplesSinceFlush >= FLUSH_INTERVAL_SAMPLES) {
                writer.flush();
                samplesSinceFlush = 0;
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            closeAfterWriteFailure(exception);
            return false;
        }
    }

    /**
     * 保持している出力資源を閉じます。
     * @param reason 処理を行う理由を表す文字列。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    boolean close(String reason) {
        if (closed) {
            return true;
        }

        boolean succeeded = true;
        try {
            JsonObject record = baseRecord("session_end");
            record.addProperty("endedAtUtc", Instant.now().toString());
            record.addProperty("reason", reason);
            writeLine(record);
            writer.flush();
        } catch (IOException | RuntimeException ignored) {
            succeeded = false;
        } finally {
            try {
                writer.close();
            } catch (IOException ignored) {
                succeeded = false;
            }
            closed = true;
        }
        return succeeded;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code writeSessionStart}が示すデータを出力先へ書き込みます。
     * @param sessionStartedAtUtc 仕様書に個別説明がないため、現在の処理内容から推定した、{@code sessionStartedAtUtc}として使用される入力値。
     * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
     * @param holdStopTargetUntilStop 仕様書に個別説明がないため、{@code holdStopTargetUntilStop}が示す条件の有効・無効を表す値。
     */
    private void writeSessionStart(
            Instant sessionStartedAtUtc,
            Notch fixedNotch,
            boolean holdStopTargetUntilStop) throws IOException {
        JsonObject record = baseRecord("session_start");
        record.addProperty("startedAtUtc", sessionStartedAtUtc.toString());
        record.addProperty("selectionMode", "FIXED_FOR_TEST");
        record.addProperty("fixedTestNotch", fixedNotch.name());
        record.addProperty("holdStopTargetUntilStop", holdStopTargetUntilStop);

        JsonObject units = new JsonObject();
        units.addProperty("speed", "blocks/s");
        units.addProperty("acceleration", "blocks/s^2");
        units.addProperty("distance", "blocks");
        units.addProperty("time", "seconds");
        units.addProperty(
                "measuredAccelerationConvention",
                "delta(abs(speed)) per server tick; braking is negative");
        units.addProperty(
                "measuredAccelerationScope",
                "one whole server tick; not the acceleration caused by one approachTargetSpeed call");
        units.addProperty(
                "signedVelocityAccelerationConvention",
                "delta(signed speed) per server tick");
        record.add("units", units);

        JsonObject fieldSemantics = new JsonObject();
        fieldSemantics.addProperty(
                "effectiveAccelerationBlocksPerSecondSquared",
                "stored response-model value retained for compatibility; use responseState.activeEffectiveAccelerationBlocksPerSecondSquared for acceleration returned by the notch modifier in this sample");
        fieldSemantics.addProperty(
                "transitionTimebase",
                "one response step per server tick containing at least one notch-modified approachTargetSpeed call");
        record.add("fieldSemantics", fieldSemantics);

        writeLine(record);
        writer.flush();
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code baseRecord}としてまとめられている処理を実行します。
     * @param recordType 仕様書に個別説明がないため、現在の処理内容から推定した、{@code recordType}として使用される入力値。
     * @return 処理によって得られた結果。
     */
    private JsonObject baseRecord(String recordType) {
        JsonObject record = new JsonObject();
        record.addProperty("schemaVersion", SCHEMA_VERSION);
        record.addProperty("recordType", recordType);
        record.addProperty("testSessionId", testSessionId.toString());
        record.addProperty("trainId", trainId.toString());
        return record;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code writeLine}が示すデータを出力先へ書き込みます。
     * @param record 仕様書に個別説明がないため、現在の処理内容から推定した、{@code record}として使用される入力値。
     */
    private void writeLine(JsonObject record) throws IOException {
        writer.write(GSON.toJson(record));
        writer.newLine();
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code closeAfterWriteFailure}が対象とする資源を終了処理して閉じます。
     * @param exception 記録または処理対象の例外。
     */
    private void closeAfterWriteFailure(Exception exception) {
        try {
            JsonObject record = baseRecord("error");
            record.addProperty("occurredAtUtc", Instant.now().toString());
            record.addProperty("operation", "sample_write_failed");
            record.addProperty("message", conciseMessage(exception));
            writeLine(record);
            writer.flush();
        } catch (IOException | RuntimeException ignored) {
            // The manager reports the failure through the server logger.
        } finally {
            try {
                writer.close();
            } catch (IOException ignored) {
                // The manager reports the original failure.
            }
            closed = true;
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code createUniquePath}が示す新しい値または資源を生成します。
     * @param outputDirectory ログファイルを作成する出力ディレクトリ。
     * @param trainId 対象列車を識別するUUID。
     * @param sessionStartedAtUtc 仕様書に個別説明がないため、現在の処理内容から推定した、{@code sessionStartedAtUtc}として使用される入力値。
     * @return 処理によって得られた結果。
     */
    private static Path createUniquePath(
            Path outputDirectory,
            UUID trainId,
            Instant sessionStartedAtUtc) {
        String baseName = "train-"
                + trainId
                + "-"
                + FILE_TIMESTAMP_FORMAT.format(sessionStartedAtUtc);
        Path candidate = outputDirectory.resolve(baseName + ".log");
        int suffix = 1;

        while (Files.exists(candidate)) {
            candidate = outputDirectory.resolve(baseName + "-" + suffix + ".log");
            suffix++;
        }
        return candidate;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code addNullableNumber}としてまとめられている処理を実行します。
     * @param record 仕様書に個別説明がないため、現在の処理内容から推定した、{@code record}として使用される入力値。
     * @param name 対象を識別する名前。
     * @param value 処理対象の値。
     */
    private static void addNullableNumber(JsonObject record, String name, Number value) {
        if (value == null) {
            record.add(name, JsonNull.INSTANCE);
        } else {
            record.addProperty(name, value);
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code addNullableString}としてまとめられている処理を実行します。
     * @param record 仕様書に個別説明がないため、現在の処理内容から推定した、{@code record}として使用される入力値。
     * @param name 対象を識別する名前。
     * @param value 処理対象の値。
     */
    private static void addNullableString(JsonObject record, String name, String value) {
        if (value == null) {
            record.add(name, JsonNull.INSTANCE);
        } else {
            record.addProperty(name, value);
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code conciseMessage}としてまとめられている処理を実行します。
     * @param exception 記録または処理対象の例外。
     * @return 処理によって得られた結果。
     */
    private static String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }
}

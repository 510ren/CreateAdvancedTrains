package dev.edudio.createadvancedtrains.debug.traindata;

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
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import dev.edudio.createadvancedtrains.debug.traindata.TrainDataSnapshot.DestinationSnapshot;
import dev.edudio.createadvancedtrains.debug.traindata.TrainDataSnapshot.LeadingTravellingPointSnapshot;
import dev.edudio.createadvancedtrains.debug.traindata.TrainDataSnapshot.NavigationSnapshot;
import dev.edudio.createadvancedtrains.debug.traindata.TrainDataSnapshot.RailEdgePointSnapshot;
import dev.edudio.createadvancedtrains.debug.traindata.TrainDataSnapshot.RailPositionSnapshot;
import dev.edudio.createadvancedtrains.debug.traindata.TrainDataSnapshot.TrackNodeLocationSnapshot;
import dev.edudio.createadvancedtrains.debug.traindata.TrainDataSnapshot.WorldPositionSnapshot;

/**
 * Writes one train's data for one logging session as JSON Lines.
 */
final class TrainDataLogWriter {

    private static final int SCHEMA_VERSION = 1;
    private static final int FLUSH_INTERVAL_SAMPLES = 20;

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("uuuuMMdd'T'HHmmss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final UUID trainId;
    private final Path path;
    private final BufferedWriter writer;

    private int samplesSinceFlush;
    private boolean closed;

    private TrainDataLogWriter(
            UUID trainId,
            Path path,
            BufferedWriter writer) {
        this.trainId = trainId;
        this.path = path;
        this.writer = writer;
    }

    public static TrainDataLogWriter open(
            Path outputDirectory,
            UUID trainId,
            Instant sessionStartedAtUtc,
            String dimension,
            int sampleIntervalTicks) throws IOException {
        Files.createDirectories(outputDirectory);

        Path path = createUniquePath(outputDirectory, trainId, sessionStartedAtUtc);
        BufferedWriter bufferedWriter = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        TrainDataLogWriter logWriter = new TrainDataLogWriter(trainId, path, bufferedWriter);

        try {
            logWriter.writeSessionStart(
                    sessionStartedAtUtc,
                    dimension,
                    sampleIntervalTicks);
            return logWriter;
        } catch (IOException | RuntimeException exception) {
            logWriter.writeErrorAndClose("session_start_write_failed", exception);
            throw exception;
        }
    }

    public Path getPath() {
        return path;
    }

    public boolean writeSample(TrainDataSnapshot snapshot) {
        if (closed) {
            return false;
        }

        try {
            JsonObject record = baseRecord("sample");
            record.addProperty("serverTick", snapshot.serverTick());
            record.addProperty("levelGameTime", snapshot.levelGameTime());

            JsonObject speed = new JsonObject();
            speed.addProperty("blocksPerTick", snapshot.speedBlocksPerTick());
            speed.addProperty("blocksPerSecond", snapshot.speedBlocksPerSecond());
            record.add("speed", speed);

            JsonObject targetSpeed = new JsonObject();
            targetSpeed.addProperty("blocksPerTick", snapshot.targetSpeedBlocksPerTick());
            targetSpeed.addProperty("blocksPerSecond", snapshot.targetSpeedBlocksPerSecond());
            record.add("targetSpeed", targetSpeed);

            JsonObject createAcceleration = new JsonObject();
            createAcceleration.addProperty(
                    "blocksPerTickSquared",
                    snapshot.accelerationBlocksPerTickSquared());
            createAcceleration.addProperty(
                    "blocksPerSecondSquared",
                    snapshot.accelerationBlocksPerSecondSquared());
            record.add("createAcceleration", createAcceleration);

            JsonObject observation = new JsonObject();
            observation.addProperty("source", "Create 6.0.8 Train");
            observation.addProperty("controlModifiedByTrainDataDebugger", false);
            record.add("observation", observation);

            record.add("navigation", navigation(snapshot.navigation()));

            writeLine(record);
            samplesSinceFlush++;

            if (samplesSinceFlush >= FLUSH_INTERVAL_SAMPLES) {
                writer.flush();
                samplesSinceFlush = 0;
            }

            return true;
        } catch (IOException | RuntimeException exception) {
            writeErrorAndClose("sample_write_failed", exception);
            return false;
        }
    }

    public boolean close(String reason) {
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
        } catch (IOException | RuntimeException exception) {
            succeeded = false;
            writeErrorBeforeClose("session_end_write_failed", exception);
        } finally {
            if (!closeWriter()) {
                succeeded = false;
            }
        }

        return succeeded;
    }

    private void writeSessionStart(
            Instant sessionStartedAtUtc,
            String dimension,
            int sampleIntervalTicks) throws IOException {
        JsonObject record = baseRecord("session_start");
        record.addProperty("startedAtUtc", sessionStartedAtUtc.toString());
        record.addProperty("dimension", dimension);
        record.addProperty("sampleIntervalTicks", sampleIntervalTicks);

        JsonObject units = new JsonObject();
        units.addProperty("speedInternal", "blocks/tick");
        units.addProperty("speedDisplay", "blocks/s");
        units.addProperty("accelerationInternal", "blocks/tick^2");
        units.addProperty("accelerationDisplay", "blocks/s^2");
        record.add("units", units);

        writeLine(record);
        writer.flush();
    }

    private JsonObject baseRecord(String recordType) {
        JsonObject record = new JsonObject();
        record.addProperty("schemaVersion", SCHEMA_VERSION);
        record.addProperty("recordType", recordType);
        record.addProperty("trainId", trainId.toString());
        return record;
    }

    private static JsonObject navigation(NavigationSnapshot snapshot) {
        JsonObject navigation = new JsonObject();
        navigation.addProperty("leadingPointReference", snapshot.leadingPointReference());
        addNullable(
                navigation,
                "leadingTravellingPoint",
                leadingTravellingPoint(snapshot.leadingTravellingPoint()));
        addNullable(
                navigation,
                "destination",
                destination(snapshot.destination()));
        addNullableNumber(
                navigation,
                "distanceToDestinationBlocks",
                snapshot.distanceToDestinationBlocks());
        navigation.addProperty("destinationAvailability", snapshot.destinationAvailability());
        return navigation;
    }

    private static JsonObject leadingTravellingPoint(LeadingTravellingPointSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        JsonObject leadingTravellingPoint = new JsonObject();
        addNullable(
                leadingTravellingPoint,
                "railPosition",
                railPosition(snapshot.railPosition()));
        return leadingTravellingPoint;
    }

    private static JsonObject destination(DestinationSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        JsonObject destination = new JsonObject();
        destination.addProperty("type", snapshot.type());
        addNullableString(destination, "stationId", snapshot.stationId());
        addNullable(destination, "edgePoint", railEdgePoint(snapshot.edgePoint()));
        return destination;
    }

    private static JsonObject railPosition(RailPositionSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        JsonObject railPosition = new JsonObject();
        addNullableString(railPosition, "graphId", snapshot.graphId());
        addNullable(railPosition, "node1", trackNodeLocation(snapshot.node1()));
        addNullable(railPosition, "node2", trackNodeLocation(snapshot.node2()));
        railPosition.addProperty("positionOnEdgeBlocks", snapshot.positionOnEdgeBlocks());
        railPosition.addProperty("upsideDown", snapshot.upsideDown());
        addNullable(railPosition, "worldPosition", worldPosition(snapshot.worldPosition()));
        return railPosition;
    }

    private static JsonObject railEdgePoint(RailEdgePointSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        JsonObject edgePoint = new JsonObject();
        addNullableString(edgePoint, "graphId", snapshot.graphId());
        addNullable(edgePoint, "endpoint1", trackNodeLocation(snapshot.endpoint1()));
        addNullable(edgePoint, "endpoint2", trackNodeLocation(snapshot.endpoint2()));
        edgePoint.addProperty("storedPositionBlocks", snapshot.storedPositionBlocks());
        addNullableNumber(
                edgePoint,
                "resolvedPositionOnEdgeBlocks",
                snapshot.resolvedPositionOnEdgeBlocks());
        addNullable(edgePoint, "worldPosition", worldPosition(snapshot.worldPosition()));
        return edgePoint;
    }

    private static JsonObject trackNodeLocation(TrackNodeLocationSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        JsonObject node = new JsonObject();
        addNullableString(node, "dimension", snapshot.dimension());

        JsonObject networkPosition = new JsonObject();
        networkPosition.addProperty("x", snapshot.networkX());
        networkPosition.addProperty("y", snapshot.networkY());
        networkPosition.addProperty("z", snapshot.networkZ());
        networkPosition.addProperty("yOffsetPixels", snapshot.yOffsetPixels());
        node.add("networkPosition", networkPosition);

        JsonObject worldPosition = new JsonObject();
        worldPosition.addProperty("x", snapshot.worldX());
        worldPosition.addProperty("y", snapshot.worldY());
        worldPosition.addProperty("z", snapshot.worldZ());
        node.add("worldPosition", worldPosition);
        return node;
    }

    private static JsonObject worldPosition(WorldPositionSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        JsonObject position = new JsonObject();
        addNullableString(position, "dimension", snapshot.dimension());
        position.addProperty("x", snapshot.x());
        position.addProperty("y", snapshot.y());
        position.addProperty("z", snapshot.z());
        return position;
    }

    private static void addNullable(
            JsonObject parent,
            String name,
            JsonObject value) {
        parent.add(name, value == null ? JsonNull.INSTANCE : value);
    }

    private static void addNullableString(
            JsonObject parent,
            String name,
            String value) {
        if (value == null) {
            parent.add(name, JsonNull.INSTANCE);
        } else {
            parent.addProperty(name, value);
        }
    }

    private static void addNullableNumber(
            JsonObject parent,
            String name,
            Double value) {
        if (value == null) {
            parent.add(name, JsonNull.INSTANCE);
        } else {
            parent.addProperty(name, value);
        }
    }

    private void writeLine(JsonObject record) throws IOException {
        writer.write(GSON.toJson(record));
        writer.newLine();
    }

    private void writeErrorAndClose(
            String operation,
            Exception exception) {
        writeErrorBeforeClose(operation, exception);
        closeWriter();
    }

    private void writeErrorBeforeClose(
            String operation,
            Exception exception) {
        try {
            JsonObject record = baseRecord("error");
            record.addProperty("occurredAtUtc", Instant.now().toString());
            record.addProperty("operation", operation);
            record.addProperty("message", conciseMessage(exception));
            writeLine(record);
            writer.flush();
        } catch (IOException | RuntimeException ignored) {
            // The caller reports the original failure through the server logger.
        }
    }

    private boolean closeWriter() {
        if (closed) {
            return true;
        }

        closed = true;

        try {
            writer.close();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

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

    private static String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }
}

package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.ApneaEvent;
import com.adriangarett.sleephqmcp.domain.ApneaScanResult;
import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import com.adriangarett.sleephqmcp.support.EdfParser;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class WaveformService {

    private final SleepHqClient sleepHqClient;
    private final RestClient s3RestClient;
    private final ClinicalContextProperties clinical;

    public WaveformService(SleepHqClient sleepHqClient,
                           @Qualifier("s3RestClient") RestClient s3RestClient,
                           ClinicalContextProperties clinical) {
        this.sleepHqClient = sleepHqClient;
        this.s3RestClient  = s3RestClient;
        this.clinical      = clinical;
    }

    public String getWaveform(String fileId, int startSeconds, int maxSeconds) {
        // 1. Fetch file metadata from SleepHQ
        String fileJson = sleepHqClient.getImportFile(fileId);
        JsonNode attrs  = JsonApi.attributes(JsonApi.parse(fileJson));

        String downloadUrl = attrs.path("download_url").asText(null);
        String filename    = attrs.path("name").asText(null);

        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("download_url missing for file " + fileId);
        }
        if (!downloadUrl.startsWith("https://")) {
            throw new IllegalArgumentException("download_url is not HTTPS for file " + fileId);
        }

        // 2. Download EDF binary — use URI.create() so signed URL query params are not template variables
        URI uri = URI.create(downloadUrl);
        byte[] edfBytes = downloadEdf(uri, fileId);

        // 3. Parse EDF and attach filename from metadata
        WaveformResult parsed = EdfParser.parse(edfBytes, startSeconds, maxSeconds);
        WaveformResult result = new WaveformResult(
                filename != null ? filename : "",
                parsed.startDatetime(),
                parsed.durationSeconds(),
                parsed.channels()
        );

        try {
            return JsonApi.mapper().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize waveform result", e);
        }
    }

    public String getWaveformByDate(String teamId, String date, int startSeconds, int maxSeconds) {
        String resolvedTeamId = teamId != null && !teamId.isBlank() ? teamId : clinical.defaultTeamId();
        if (resolvedTeamId == null || resolvedTeamId.isBlank()) {
            throw new IllegalArgumentException("Required teamId is missing and no default SLEEPHQ_TEAM_ID is configured");
        }
        String fileId = resolveFileIdByDate(resolvedTeamId, date);
        return getWaveform(fileId, startSeconds, maxSeconds);
    }

    public String scanApneaEvents(String fileId, String teamId, String date, Double threshold, Integer minDurationSeconds) {
        String targetFileId = fileId;
        if (targetFileId == null || targetFileId.isBlank()) {
            if (date == null || date.isBlank()) {
                throw new IllegalArgumentException("Either fileId or date must be provided");
            }
            String resolvedTeamId = teamId != null && !teamId.isBlank() ? teamId : clinical.defaultTeamId();
            if (resolvedTeamId == null || resolvedTeamId.isBlank()) {
                throw new IllegalArgumentException("Required teamId is missing and no default SLEEPHQ_TEAM_ID is configured");
            }
            targetFileId = resolveFileIdByDate(resolvedTeamId, date);
        }

        // 1. Fetch file metadata to get download URL
        String fileJson = sleepHqClient.getImportFile(targetFileId);
        JsonNode attrs  = JsonApi.attributes(JsonApi.parse(fileJson));
        String downloadUrl = attrs.path("download_url").asText(null);
        String filename    = attrs.path("name").asText("");

        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("download_url missing for file " + targetFileId);
        }

        // 2. Download full EDF file
        URI uri = URI.create(downloadUrl);
        byte[] edfBytes = downloadEdf(uri, targetFileId);

        // 3. Parse full file (use a large duration like 12 hours = 43200 seconds)
        int twelveHours = 12 * 3600;
        WaveformResult fullResult = EdfParser.parse(edfBytes, 0, twelveHours);

        // 4. Find Flow channel
        WaveformChannel flowChannel = null;
        for (WaveformChannel ch : fullResult.channels()) {
            String lbl = ch.label().toLowerCase(Locale.ROOT);
            if (lbl.startsWith("flow")) {
                flowChannel = ch;
                break;
            }
        }

        if (flowChannel == null) {
            throw new IllegalArgumentException("No flow respiration channel found in file " + filename);
        }

        // 5. Run apnea/hypopnea detection algorithm
        double Fs = flowChannel.sampleRate();
        List<Double> samples = flowChannel.samples();
        int totalSamples = samples.size();

        double hypopneaLimit = threshold != null ? threshold : 0.15;
        double apneaLimit = 0.04;
        int minSamples = (int) Math.round((minDurationSeconds != null ? minDurationSeconds : 10) * Fs);

        // Compute 4-second smoothing window size
        int windowSize = (int) Math.round(4.0 * Fs);
        if (windowSize <= 0) windowSize = 1;

        // Precompute moving average of absolute flow (flow envelope)
        double[] envelope = new double[totalSamples];
        double sum = 0.0;
        for (int i = 0; i < totalSamples; i++) {
            sum += Math.abs(samples.get(i));
            if (i >= windowSize) {
                sum -= Math.abs(samples.get(i - windowSize));
            }
            int denom = Math.min(i + 1, windowSize);
            envelope[i] = sum / denom;
        }

        // Detect contiguous blocks where envelope < hypopneaLimit
        List<ApneaEvent> events = new ArrayList<>();
        boolean inEvent = false;
        int eventStartIdx = -1;

        for (int i = 0; i < totalSamples; i++) {
            boolean belowLimit = envelope[i] < hypopneaLimit;
            if (belowLimit && !inEvent) {
                inEvent = true;
                eventStartIdx = i;
            } else if (!belowLimit && inEvent) {
                inEvent = false;
                int durationSamples = i - eventStartIdx;
                if (durationSamples >= minSamples) {
                    double startSec = eventStartIdx / Fs;
                    double durationSec = durationSamples / Fs;
                    String classification = classifyEvent(samples, eventStartIdx, i, Fs, apneaLimit);
                    events.add(createEvent(fullResult.startDatetime(), startSec, durationSec, classification));
                }
            }
        }

        // Handle event at the very end
        if (inEvent) {
            int durationSamples = totalSamples - eventStartIdx;
            if (durationSamples >= minSamples) {
                double startSec = eventStartIdx / Fs;
                double durationSec = durationSamples / Fs;
                String classification = classifyEvent(samples, eventStartIdx, totalSamples, Fs, apneaLimit);
                events.add(createEvent(fullResult.startDatetime(), startSec, durationSec, classification));
            }
        }

        // 6. Serialize and return response
        ApneaScanResult scanResult = new ApneaScanResult(
                filename,
                fullResult.startDatetime(),
                fullResult.durationSeconds(),
                flowChannel.label(),
                hypopneaLimit,
                events
        );

        try {
            return JsonApi.mapper().writeValueAsString(scanResult);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize apnea scan result", e);
        }
    }

    private String classifyEvent(List<Double> samples, int startIdx, int endIdx, double Fs, double apneaLimit) {
        int N = endIdx - startIdx;
        double sumAbs = 0.0;
        for (int i = startIdx; i < endIdx; i++) {
            sumAbs += Math.abs(samples.get(i));
        }
        double avgEnvelope = sumAbs / N;

        if (avgEnvelope >= apneaLimit) {
            return "HYPOPNEA";
        }

        // For Apneas, check for 4 Hz Forced Oscillation Technique (FOT) oscillations
        // Use the middle 50% of the event to avoid edge leakage from transition lags
        int cropStart = startIdx + N / 4;
        int cropEnd = endIdx - N / 4;
        int cropN = cropEnd - cropStart;

        if (cropN < 25) { // too short, fallback to full window
            cropStart = startIdx;
            cropEnd = endIdx;
            cropN = N;
        }

        if (cropN < 10) {
            return "APNEA_OBSTRUCTIVE";
        }

        double cosSum = 0.0;
        double sinSum = 0.0;
        for (int i = cropStart; i < cropEnd; i++) {
            double angle = 2.0 * Math.PI * 4.0 * (i - cropStart) / Fs;
            cosSum += samples.get(i) * Math.cos(angle);
            sinSum += samples.get(i) * Math.sin(angle);
        }
        double amp4Hz = Math.sqrt(Math.pow(cosSum * 2.0 / cropN, 2) + Math.pow(sinSum * 2.0 / cropN, 2));

        // Threshold for FOT 4Hz amplitude detection
        if (amp4Hz >= 0.003) {
            return "APNEA_CENTRAL";
        } else {
            return "APNEA_OBSTRUCTIVE";
        }
    }

    private byte[] downloadEdf(URI uri, String fileId) {
        try {
            byte[] edfBytes = s3RestClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(byte[].class);
            if (edfBytes == null || edfBytes.length == 0) {
                throw new IllegalStateException("Downloaded empty file for fileId " + fileId);
            }
            return edfBytes;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Download failed for file " + fileId + " (HTTP " + e.getStatusCode().value() +
                    ") — the signed URL may have expired (5-minute TTL)", e);
        }
    }

    private String resolveFileIdByDate(String teamId, String date) {
        String cleanDate = date.replace("-", "").trim();
        if (cleanDate.length() != 8) {
            throw new IllegalArgumentException("Invalid date format: " + date + ". Expected YYYY-MM-DD.");
        }

        int pageNum = 1;
        while (pageNum <= 5) {
            String filesJson = sleepHqClient.listTeamFiles(teamId, pageNum, 100);
            JsonNode root = JsonApi.parse(filesJson);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }
            for (JsonNode item : data) {
                String name = item.path("attributes").path("name").asText("");
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (lowerName.contains(cleanDate) && lowerName.contains("brp.edf")) {
                    return item.path("id").asText();
                }
            }
            if (data.size() < 100) {
                break;
            }
            pageNum++;
        }
        throw new IllegalArgumentException("No BRP.edf file found for date " + date + " (teamId: " + teamId + ")");
    }

    private ApneaEvent createEvent(String startDatetimeStr, double startSec, double durationSec, String classification) {
        LocalDateTime baseTime = LocalDateTime.parse(startDatetimeStr);
        LocalDateTime eventTime = baseTime.plusNanos((long) (startSec * 1_000_000_000L));

        long hr = (long) (startSec / 3600);
        long min = (long) ((startSec % 3600) / 60);
        long sec = (long) (startSec % 60);
        String offsetStr = String.format("%02d:%02d:%02d", hr, min, sec);

        return new ApneaEvent(
                offsetStr,
                startSec,
                durationSec,
                eventTime.toString(),
                classification
        );
    }
}

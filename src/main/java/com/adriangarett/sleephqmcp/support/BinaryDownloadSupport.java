package com.adriangarett.sleephqmcp.support;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;

/**
 * Downloads import file bytes from signed S3 URLs.
 */
public final class BinaryDownloadSupport {

    private BinaryDownloadSupport() {}

    public static byte[] download(RestClient s3RestClient, URI uri, String fileId) {
        try {
            byte[] bytes = s3RestClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(byte[].class);
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("Downloaded empty file for fileId " + fileId);
            }
            return bytes;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Download failed for file " + fileId + " (HTTP " + e.getStatusCode().value() +
                            ") — the signed URL may have expired (5-minute TTL)", e);
        }
    }
}

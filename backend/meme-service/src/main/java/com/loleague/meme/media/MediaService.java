package com.loleague.meme.media;

import com.loleague.common.security.CurrentUser;
import com.loleague.common.web.ApiException;
import com.loleague.meme.config.MemeProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Browser uploads go straight to S3 with a short-lived presigned PUT (our servers never proxy image bytes).
 * The size and content-type are part of the signature, and we re-check the object with HEAD before using it.
 */
@Service
public class MediaService {

    public enum Purpose { MEME, AVATAR }

    public record PresignRequest(Purpose purpose, String contentType, long contentLength) {
    }

    public record PresignResponse(String uploadUrl, String key, String publicUrl, Instant expiresAt,
                                  Map<String, String> headers) {
    }

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private final S3Presigner presigner;
    private final S3Client s3;
    private final MemeProperties.Aws aws;

    public MediaService(S3Presigner presigner, S3Client s3, MemeProperties props) {
        this.presigner = presigner;
        this.s3 = s3;
        this.aws = props.aws();
    }

    public PresignResponse presign(CurrentUser me, PresignRequest req) {
        if (req.purpose() == null) {
            throw ApiException.badRequest("purpose is required");
        }
        String ext = EXTENSIONS.get(req.contentType());
        if (ext == null) {
            throw ApiException.badRequest("Only JPG, PNG, WEBP or GIF images are allowed");
        }
        long max = req.purpose() == Purpose.MEME ? aws.maxMemeBytes() : aws.maxAvatarBytes();
        if (req.contentLength() <= 0 || req.contentLength() > max) {
            throw ApiException.badRequest("Image must be smaller than " + (max / (1024 * 1024)) + " MB");
        }
        String folder = req.purpose() == Purpose.MEME
                ? LocalDate.now(ZoneId.of("Asia/Kolkata")).toString().substring(0, 7) + "/"
                : "";
        String key = ownerPrefix(me, req.purpose()) + folder + UUID.randomUUID() + "." + ext;

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(aws.bucket())
                .key(key)
                .contentType(req.contentType())
                .contentLength(req.contentLength())
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(p -> p
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(put));

        return new PresignResponse(presigned.url().toString(), key, aws.publicUrl(key), presigned.expiration(),
                Map.of("Content-Type", req.contentType(),
                        "Cache-Control", "public, max-age=31536000, immutable"));
    }

    /** Makes sure the key belongs to this student, exists in S3 and respects the size limit. */
    public String verifyUploaded(CurrentUser me, Purpose purpose, String key) {
        if (key == null || !key.startsWith(ownerPrefix(me, purpose)) || key.contains("..")) {
            throw ApiException.badRequest("Invalid upload");
        }
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(aws.bucket()).key(key).build());
            long max = purpose == Purpose.MEME ? aws.maxMemeBytes() : aws.maxAvatarBytes();
            if (head.contentLength() > max || !EXTENSIONS.containsKey(head.contentType())) {
                delete(key);
                throw ApiException.badRequest("Upload rejected (type or size)");
            }
        } catch (NoSuchKeyException e) {
            throw ApiException.badRequest("Upload not found. Please upload the image again.");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw ApiException.badRequest("Upload not found. Please upload the image again.");
            }
            throw e;
        }
        return aws.publicUrl(key);
    }

    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(aws.bucket()).key(key).build());
        } catch (Exception ignored) {
            // Orphans are harmless; an S3 lifecycle rule can clean them up.
        }
    }

    /** Every key embeds the owner, so nobody can attach someone else's upload. */
    private static String ownerPrefix(CurrentUser me, Purpose purpose) {
        return switch (purpose) {
            case MEME -> "memes/" + me.collegeId() + "/" + me.id() + "/";
            case AVATAR -> "avatars/" + me.id() + "/";
        };
    }
}

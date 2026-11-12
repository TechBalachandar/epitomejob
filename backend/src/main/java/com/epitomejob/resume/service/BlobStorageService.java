package com.epitomejob.resume.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Object storage behind a plain S3 API (RustFS locally, S3-compatible
 * anywhere). The MinIO Java SDK is used purely as an S3 client — no
 * MinIO-specific server feature is required.
 */
@Service
public class BlobStorageService {

  private final MinioClient minio;
  private final String bucket;

  public BlobStorageService(@Value("${app.minio.endpoint}") String endpoint,
                            @Value("${app.minio.access-key}") String accessKey,
                            @Value("${app.minio.secret-key}") String secretKey,
                            @Value("${app.minio.bucket}") String bucket) {
    this(MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build(), bucket);
  }

  /** Test seam: inject a mocked client instead of building a real one. */
  BlobStorageService(MinioClient minio, String bucket) {
    this.minio = minio;
    this.bucket = bucket;
  }

  public void put(String path, InputStream data, long size, String contentType) throws Exception {
    if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
      minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
    minio.putObject(PutObjectArgs.builder().bucket(bucket).object(path)
        .stream(data, size, -1).contentType(contentType).build());
  }
}

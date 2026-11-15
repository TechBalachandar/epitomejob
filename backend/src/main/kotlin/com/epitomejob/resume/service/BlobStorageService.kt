package com.epitomejob.resume.service

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream

/**
 * Object storage behind a plain S3 API (RustFS locally, S3-compatible
 * anywhere). The MinIO Java SDK is used purely as an S3 client — no
 * MinIO-specific server feature is required.
 */
@Service
class BlobStorageService internal constructor(
  private val minio: MinioClient,
  private val bucket: String
) {

  /**
   * @Autowired is required here: this class has two constructors, and with
   * more than one Spring picks none of them implicitly — without it the
   * app fails to boot with "No default constructor found".
   */
  @Autowired
  constructor(
    @Value("\${app.minio.endpoint}") endpoint: String,
    @Value("\${app.minio.access-key}") accessKey: String,
    @Value("\${app.minio.secret-key}") secretKey: String,
    @Value("\${app.minio.bucket}") bucket: String
  ) : this(MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build(), bucket)

  fun put(path: String, data: InputStream, size: Long, contentType: String) {
    if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
      minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
    }
    minio.putObject(
      PutObjectArgs.builder().bucket(bucket).object(path)
        .stream(data, size, -1).contentType(contentType).build()
    )
  }
}

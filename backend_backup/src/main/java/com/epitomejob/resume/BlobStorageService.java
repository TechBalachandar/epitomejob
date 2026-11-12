package com.epitomejob.resume;
import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.InputStream;
@Service
public class BlobStorageService {
  private final MinioClient minio;
  private final String bucket;
  public BlobStorageService(@Value("${app.minio.endpoint}") String endpoint,
                            @Value("${app.minio.access-key}") String accessKey,
                            @Value("${app.minio.secret-key}") String secretKey,
                            @Value("${app.minio.bucket}") String bucket) {
    this.minio = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
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

package com.epitomejob.resume.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlobStorageServiceTest {

  @Mock
  private MinioClient minio;

  @Test
  void putCreatesBucketWhenMissing() throws Exception {
    when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

    new BlobStorageService(minio, "resumes")
        .put("u1/file.pdf", new ByteArrayInputStream("x".getBytes()), 1, "application/pdf");

    verify(minio).makeBucket(any(MakeBucketArgs.class));
    verify(minio).putObject(any(PutObjectArgs.class));
  }

  @Test
  void putSkipsBucketCreationWhenBucketExists() throws Exception {
    when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

    new BlobStorageService(minio, "resumes")
        .put("u1/file.pdf", new ByteArrayInputStream("x".getBytes()), 1, "application/pdf");

    verify(minio, never()).makeBucket(any(MakeBucketArgs.class));
    verify(minio).putObject(any(PutObjectArgs.class));
  }
}

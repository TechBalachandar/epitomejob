package com.epitomejob.resume.service

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.io.ByteArrayInputStream

@ExtendWith(MockitoExtension::class)
class BlobStorageServiceTest {

  @Mock
  private lateinit var minio: MinioClient

  @Test
  fun putCreatesBucketWhenMissing() {
    Mockito.`when`(minio.bucketExists(any(BucketExistsArgs::class.java))).thenReturn(false)

    BlobStorageService(minio, "resumes")
      .put("u1/file.pdf", ByteArrayInputStream("x".toByteArray()), 1, "application/pdf")

    verify(minio).makeBucket(any(MakeBucketArgs::class.java))
    verify(minio).putObject(any(PutObjectArgs::class.java))
  }

  @Test
  fun putSkipsBucketCreationWhenBucketExists() {
    Mockito.`when`(minio.bucketExists(any(BucketExistsArgs::class.java))).thenReturn(true)

    BlobStorageService(minio, "resumes")
      .put("u1/file.pdf", ByteArrayInputStream("x".toByteArray()), 1, "application/pdf")

    verify(minio, never()).makeBucket(any(MakeBucketArgs::class.java))
    verify(minio).putObject(any(PutObjectArgs::class.java))
  }
}

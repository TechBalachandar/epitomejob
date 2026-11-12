package com.epitomejob.job.service;

import com.epitomejob.job.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Adzuna provider stub (W2 contract §8). No network calls in W2. Bean only
 * when {@code epitomejob.jobs.provider=adzuna}. On first use it requires the
 * {@code ADZUNA_APP_ID} and {@code ADZUNA_APP_KEY} env vars and throws
 * {@link IllegalStateException} naming any that are missing; with both present
 * it throws {@link UnsupportedOperationException} because live Adzuna calls
 * are out of scope for W2.
 */
public class AdzunaJobProvider implements JobProvider {

  public static final String ENV_APP_ID = "ADZUNA_APP_ID";
  public static final String ENV_APP_KEY = "ADZUNA_APP_KEY";

  private final Function<String, String> env;

  public AdzunaJobProvider() {
    this(System::getenv);
  }

  AdzunaJobProvider(Function<String, String> env) {
    this.env = env;
  }

  @Override
  public Page<Job> search(String query, String location, String workType,
                          Boolean sponsorsH1b, Pageable pageable) {
    List<String> missing = new ArrayList<>();
    if (isMissing(env.apply(ENV_APP_ID))) {
      missing.add(ENV_APP_ID);
    }
    if (isMissing(env.apply(ENV_APP_KEY))) {
      missing.add(ENV_APP_KEY);
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Adzuna provider selected but missing env vars: " + String.join(", ", missing)
              + " — set them to use epitomejob.jobs.provider=adzuna");
    }
    throw new UnsupportedOperationException(
        "Adzuna provider is a W2 stub — live Adzuna calls are not implemented yet");
  }

  private boolean isMissing(String value) {
    return value == null || value.isBlank();
  }
}

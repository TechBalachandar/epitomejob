package com.epitomejob.job.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.job.model.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Live Adzuna job provider. Calls the Adzuna Jobs API (US) and maps each
 * result to a {@link Job}. Bean only when
 * {@code epitomejob.jobs.provider=adzuna}.
 *
 * <p>Requires the {@code ADZUNA_APP_ID} and {@code ADZUNA_APP_KEY} env vars;
 * on first use it throws {@link IllegalStateException} naming any that are
 * missing. The key is never logged: error messages never include the request
 * URL (which carries the credentials as query params).
 *
 * <p>Adzuna pages are 1-based; Spring's {@link Pageable} is 0-based, so the
 * Adzuna page is {@code pageable.getPageNumber() + 1} and
 * {@code results_per_page} comes from the page size (capped at 50).
 * Adzuna's {@code what}/{@code where} handle the query/location server-side.
 * The {@code source} filter is honoured before any call (a non-Adzuna source
 * returns an empty page), and {@code sponsorsH1b=true} returns an empty page
 * because Adzuna cannot confirm H-1B sponsorship. {@code workType} is
 * filtered client-side on the fetched page: Adzuna reports contract type
 * (full/part-time, contract), not the app's Remote/Hybrid/Onsite
 * arrangement, so the returned total is Adzuna's unfiltered count when a
 * workType filter is applied.
 */
public class AdzunaJobProvider implements JobProvider {

  public static final String ENV_APP_ID = "ADZUNA_APP_ID";
  public static final String ENV_APP_KEY = "ADZUNA_APP_KEY";
  /** Source value on every Job this provider returns. Uppercase: Job
   * validates sources against {@code AppConstants.Job.SOURCES}, and the
   * source filter is case-insensitive, so {@code source=adzuna} matches. */
  public static final String SOURCE = AppConstants.Job.SOURCE_ADZUNA;

  private static final String BASE_URL = "https://api.adzuna.com/v1/api/jobs/us/search";
  private static final int MAX_RESULTS_PER_PAGE = 50;

  private final Function<String, String> env;
  private final Function<String, String> fetcher;
  private final ObjectMapper mapper;

  public AdzunaJobProvider() {
    this(System::getenv, defaultFetcher(), new ObjectMapper());
  }

  /** Test seam: inject the env lookup, the HTTP fetcher (URL to response
   * body JSON), and the JSON mapper. */
  AdzunaJobProvider(Function<String, String> env, Function<String, String> fetcher,
                    ObjectMapper mapper) {
    this.env = env;
    this.fetcher = fetcher;
    this.mapper = mapper;
  }

  private static Function<String, String> defaultFetcher() {
    RestClient client = RestClient.create();
    return url -> client.get().uri(url).retrieve().body(String.class);
  }

  @Override
  public Page<Job> search(String query, String location, String workType,
                          Boolean sponsorsH1b, String source, Pageable pageable) {
    // This provider only serves Adzuna jobs; a different source matches nothing.
    if (source != null && !source.isBlank() && !SOURCE.equalsIgnoreCase(source)) {
      return Page.empty(pageable);
    }
    // Adzuna cannot confirm H-1B sponsorship, so a sponsorship filter
    // can never match an Adzuna job.
    if (Boolean.TRUE.equals(sponsorsH1b)) {
      return Page.empty(pageable);
    }
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
    int page = pageable.getPageNumber() + 1; // Adzuna pages are 1-based.
    int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_RESULTS_PER_PAGE);
    String url = buildUrl(env.apply(ENV_APP_ID), env.apply(ENV_APP_KEY),
        query, location, page, size);
    final String body;
    try {
      body = fetcher.apply(url);
    } catch (RuntimeException e) {
      // Never include the URL or the exception message: either may carry
      // the app id/key as query params. The HTTP status alone is safe.
      String detail = "";
      if (e instanceof RestClientResponseException rce) {
        detail = " (HTTP " + rce.getStatusCode().value() + ")";
      }
      throw new IllegalStateException("Adzuna request failed" + detail);
    }
    return parse(body, workType, pageable);
  }

  private String buildUrl(String appId, String appKey, String query, String location,
                          int page, int size) {
    StringBuilder url = new StringBuilder(BASE_URL)
        .append('/').append(page)
        .append("?app_id=").append(encode(appId))
        .append("&app_key=").append(encode(appKey))
        .append("&results_per_page=").append(size);
    if (query != null && !query.isBlank()) {
      url.append("&what=").append(encode(query.strip()));
    }
    if (location != null && !location.isBlank()) {
      url.append("&where=").append(encode(location.strip()));
    }
    return url.toString();
  }

  private Page<Job> parse(String body, String workType, Pageable pageable) {
    final JsonNode root;
    try {
      root = mapper.readTree(body);
    } catch (Exception e) {
      throw new IllegalStateException("Adzuna response could not be parsed", e);
    }
    long total = root.path("count").isNumber() ? root.path("count").asLong() : 0;
    List<Job> jobs = new ArrayList<>();
    for (JsonNode item : root.path("results")) {
      Job job = toJob(item);
      if (job != null) {
        jobs.add(job);
      }
    }
    if (total == 0) {
      total = jobs.size();
    }
    if (workType != null && !workType.isBlank()) {
      jobs = jobs.stream()
          .filter(job -> job.getWorkType() != null
              && job.getWorkType().equalsIgnoreCase(workType.strip()))
          .toList();
    }
    return new PageImpl<>(jobs, pageable, total);
  }

  private Job toJob(JsonNode item) {
    String title = item.path("title").asText("");
    String company = item.path("company").path("display_name").asText("");
    String location = item.path("location").path("display_name").asText("");
    String description = item.path("description").asText("");
    String workType = mapWorkType(item);
    Integer salaryMin = intOrNull(item, "salary_min");
    Integer salaryMax = intOrNull(item, "salary_max");
    String role = item.path("category").path("label").asText("");
    if (role.isBlank()) {
      role = AppConstants.Job.ROLE_GENERAL;
    }
    Instant postedAt = parseCreated(item.path("created").asText(null));
    Job job = new Job(title, company, location, workType, salaryMin, salaryMax,
        false, List.of(), description, role, postedAt);
    job.setSource(SOURCE);
    String redirectUrl = item.path("redirect_url").asText(null);
    if (redirectUrl != null && !redirectUrl.isBlank()
        && (redirectUrl.startsWith("http://") || redirectUrl.startsWith("https://"))) {
      job.setSourceUrl(redirectUrl);
    }
    return job;
  }

  /**
   * Adzuna reports contract type/time (full_time, part_time, contract,
   * permanent), not the app's Remote/Hybrid/Onsite arrangement — that is
   * not in the Adzuna response, so the contract is the honest workType.
   */
  private String mapWorkType(JsonNode item) {
    String raw = item.path("contract_type").asText("");
    if (raw.isBlank()) {
      raw = item.path("contract_time").asText("");
    }
    return switch (raw.strip().toLowerCase()) {
      case "part_time" -> "Part-time";
      case "contract" -> "Contract";
      case "permanent" -> "Permanent";
      default -> "Full-time";
    };
  }

  private Integer intOrNull(JsonNode item, String field) {
    JsonNode value = item.get(field);
    return value != null && value.isNumber() ? value.asInt() : null;
  }

  private Instant parseCreated(String created) {
    if (created == null || created.isBlank()) {
      return Instant.now();
    }
    try {
      return Instant.parse(created.strip());
    } catch (Exception e) {
      return Instant.now();
    }
  }

  private boolean isMissing(String value) {
    return value == null || value.isBlank();
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}

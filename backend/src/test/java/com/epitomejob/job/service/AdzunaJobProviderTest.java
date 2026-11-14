package com.epitomejob.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdzunaJobProviderTest {

  private static final String JSON = """
      {
        "count": 2,
        "results": [
          {
            "id": "111",
            "title": "Backend Engineer",
            "company": {"display_name": "Northwind"},
            "location": {"display_name": "Reston, VA"},
            "description": "Build APIs.",
            "redirect_url": "https://www.adzuna.com/details/111",
            "created": "2026-11-14T09:00:00Z",
            "category": {"label": "IT Jobs", "tag": "it-jobs"},
            "contract_type": "full_time",
            "salary_min": 120000,
            "salary_max": 160000
          },
          {
            "id": "222",
            "title": "Support Engineer",
            "company": {"display_name": "Acme"},
            "location": {"display_name": "Herndon, VA"},
            "description": "Help customers.",
            "redirect_url": "https://www.adzuna.com/details/222",
            "created": "2026-11-13T09:00:00Z",
            "category": {"label": "IT Jobs", "tag": "it-jobs"},
            "contract_type": "part_time"
          }
        ]
      }
      """;

  private AdzunaJobProvider provider(Map<String, String> env, Function<String, String> fetcher) {
    return new AdzunaJobProvider(env::get, fetcher, new ObjectMapper());
  }

  private Map<String, String> envWithKeys() {
    Map<String, String> env = new HashMap<>();
    env.put(AdzunaJobProvider.ENV_APP_ID, "test-id");
    env.put(AdzunaJobProvider.ENV_APP_KEY, "test-key");
    return env;
  }

  @Test
  void missingEnvVarsNamesBoth() {
    AdzunaJobProvider provider = provider(Map.of(), url -> JSON);

    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> provider.search(null, null, null, null, null, PageRequest.of(0, 20)));

    assertTrue(e.getMessage().contains("ADZUNA_APP_ID"), e.getMessage());
    assertTrue(e.getMessage().contains("ADZUNA_APP_KEY"), e.getMessage());
  }

  @Test
  void missingOnlyKeyNamesOnlyKey() {
    Map<String, String> env = Map.of(AdzunaJobProvider.ENV_APP_ID, "test-id");
    AdzunaJobProvider provider = provider(env, url -> JSON);

    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> provider.search(null, null, null, null, null, PageRequest.of(0, 20)));

    assertTrue(e.getMessage().contains("ADZUNA_APP_KEY"), e.getMessage());
  }

  @Test
  void mapsAdzunaResultsToJobs() {
    AtomicReference<String> seenUrl = new AtomicReference<>();
    AdzunaJobProvider provider = provider(envWithKeys(), url -> {
      seenUrl.set(url);
      return JSON;
    });

    Page<com.epitomejob.job.model.Job> page =
        provider.search("backend", "Reston", null, null, null, PageRequest.of(0, 20));

    assertEquals(2, page.getTotalElements());
    assertEquals(2, page.getContent().size());
    com.epitomejob.job.model.Job first = page.getContent().get(0);
    assertEquals("Backend Engineer", first.getTitle());
    assertEquals("Northwind", first.getCompany());
    assertEquals("Reston, VA", first.getLocation());
    assertEquals("ADZUNA", first.getSource());
    assertEquals("https://www.adzuna.com/details/111", first.getSourceUrl());
    assertEquals(120000, first.getSalaryMin());
    assertEquals(160000, first.getSalaryMax());
    assertEquals("Full-time", first.getWorkType());
    assertEquals("IT Jobs", first.getRole());
    assertEquals("2026-11-14T09:00:00Z", first.getPostedAt().toString());
    assertEquals("Part-time", page.getContent().get(1).getWorkType());
    assertNull(page.getContent().get(1).getSalaryMin(), "absent salary maps to null");
    // Adzuna pages are 1-based: Spring page 0 -> Adzuna page 1.
    assertTrue(seenUrl.get().contains("/search/1?"), seenUrl.get());
    assertTrue(seenUrl.get().contains("results_per_page=20"), seenUrl.get());
    assertTrue(seenUrl.get().contains("what=backend"), seenUrl.get());
    assertTrue(seenUrl.get().contains("where=Reston"), seenUrl.get());
  }

  @Test
  void secondPageRequestsAdzunaPageTwo() {
    AtomicReference<String> seenUrl = new AtomicReference<>();
    AdzunaJobProvider provider = provider(envWithKeys(), url -> {
      seenUrl.set(url);
      return JSON;
    });

    provider.search(null, null, null, null, null, PageRequest.of(1, 20));

    assertTrue(seenUrl.get().contains("/search/2?"), seenUrl.get());
  }

  @Test
  void nonAdzunaSourceReturnsEmptyWithoutCallingApi() {
    AtomicReference<String> seenUrl = new AtomicReference<>();
    AdzunaJobProvider provider = provider(envWithKeys(), url -> {
      seenUrl.set(url);
      return JSON;
    });

    Page<com.epitomejob.job.model.Job> page =
        provider.search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20));

    assertEquals(0, page.getTotalElements());
    assertNull(seenUrl.get(), "no API call for a non-Adzuna source");
  }

  @Test
  void sponsorsH1bFilterReturnsEmptyWithoutCallingApi() {
    AtomicReference<String> seenUrl = new AtomicReference<>();
    AdzunaJobProvider provider = provider(envWithKeys(), url -> {
      seenUrl.set(url);
      return JSON;
    });

    Page<com.epitomejob.job.model.Job> page =
        provider.search(null, null, null, true, null, PageRequest.of(0, 20));

    assertEquals(0, page.getTotalElements());
    assertNull(seenUrl.get(), "no API call when sponsorship is required");
  }

  @Test
  void workTypeFilterKeepsOnlyMatchingJobs() {
    AdzunaJobProvider provider = provider(envWithKeys(), url -> JSON);

    Page<com.epitomejob.job.model.Job> page =
        provider.search(null, null, "Part-time", null, null, PageRequest.of(0, 20));

    assertEquals(1, page.getContent().size());
    assertEquals("Support Engineer", page.getContent().get(0).getTitle());
  }
}

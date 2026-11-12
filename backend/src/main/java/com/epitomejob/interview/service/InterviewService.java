package com.epitomejob.interview.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.interview.dto.AnswerDto;
import com.epitomejob.interview.dto.QuestionDto;
import com.epitomejob.interview.dto.SessionDto;
import com.epitomejob.interview.model.Answer;
import com.epitomejob.interview.model.InterviewSession;
import com.epitomejob.interview.model.Question;
import com.epitomejob.interview.repository.AnswerRepository;
import com.epitomejob.interview.repository.InterviewSessionRepository;
import com.epitomejob.interview.repository.QuestionRepository;
import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mock interviews (M8, W2 contract §5). Deterministic rubric heuristics —
 * the exact formulas live in {@link #scoreStructure(String)} and friends.
 * A session is COMPLETED once every question for the job's role has an
 * answer; posting an answer again REPLACES the previous one (re-scored).
 */
@Service
public class InterviewService {

  private final QuestionRepository questions;
  private final InterviewSessionRepository sessions;
  private final AnswerRepository answers;
  private final JobRepository jobs;

  public InterviewService(QuestionRepository questions, InterviewSessionRepository sessions,
                          AnswerRepository answers, JobRepository jobs) {
    this.questions = questions;
    this.sessions = sessions;
    this.answers = answers;
    this.jobs = jobs;
  }

  @Transactional(readOnly = true)
  public List<QuestionDto> questionsForJob(UUID jobId) {
    Job job = findJob(jobId);
    return roleQuestions(job.getRole()).stream().map(QuestionDto::from).toList();
  }

  /** Always creates a new session — retakes are allowed. */
  @Transactional
  public SessionDto createSession(UUID jobId) {
    Job job = findJob(jobId);
    InterviewSession session = sessions.save(
        new InterviewSession(SecurityUtil.currentUserId(), jobId));
    return SessionDto.from(session, job, roleQuestions(job.getRole()), List.of());
  }

  @Transactional
  public AnswerDto submitAnswer(UUID sessionId, UUID questionId, String answerText) {
    InterviewSession session = findOwnedSession(sessionId);
    if (answerText == null || answerText.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.ANSWER_EMPTY);
    }
    Job job = findJob(session.getJobId());
    List<Question> roleQuestions = roleQuestions(job.getRole());
    Question question = roleQuestions.stream()
        .filter(q -> q.getId().equals(questionId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
            AppConstants.Errors.QUESTION_NOT_IN_SESSION));
    List<String> keywords = question.getKeywords().isEmpty()
        ? job.getSkills() : question.getKeywords();
    int structure = scoreStructure(answerText);
    int relevance = scoreRelevance(answerText, keywords);
    int specificity = scoreSpecificity(answerText, job.getSkills());
    int conciseness = scoreConciseness(answerText);
    int overall = overallScore(structure, relevance, specificity, conciseness);
    List<String> feedback = buildFeedback(answerText, keywords,
        structure, relevance, specificity, conciseness);

    Answer answer = answers.findBySessionIdAndQuestionId(sessionId, questionId)
        .orElseGet(() -> new Answer(sessionId, questionId, answerText,
            structure, relevance, specificity, conciseness, overall, feedback));
    answer.setAnswerText(answerText);
    answer.setStructure(structure);
    answer.setRelevance(relevance);
    answer.setSpecificity(specificity);
    answer.setConciseness(conciseness);
    answer.setOverallScore(overall);
    answer.setFeedback(feedback);
    Answer saved = answers.save(answer);

    maybeComplete(session, roleQuestions);
    return AnswerDto.from(saved, question.getText());
  }

  @Transactional(readOnly = true)
  public List<SessionDto> listMine() {
    UUID userId = SecurityUtil.currentUserId();
    return sessions.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public SessionDto getSession(UUID id) {
    return toDto(findOwnedSession(id));
  }

  private SessionDto toDto(InterviewSession session) {
    Job job = findJob(session.getJobId());
    List<Question> roleQuestions = roleQuestions(job.getRole());
    return SessionDto.from(session, job, roleQuestions, answers.findBySessionId(session.getId()));
  }

  private void maybeComplete(InterviewSession session, List<Question> roleQuestions) {
    if (AppConstants.Interview.STATUS_COMPLETED.equals(session.getStatus())
        || roleQuestions.isEmpty()) {
      return;
    }
    Set<UUID> answered = new HashSet<>();
    answers.findBySessionId(session.getId()).forEach(a -> answered.add(a.getQuestionId()));
    boolean allAnswered = roleQuestions.stream().allMatch(q -> answered.contains(q.getId()));
    if (allAnswered) {
      session.setStatus(AppConstants.Interview.STATUS_COMPLETED);
      if (session.getCompletedAt() == null) {
        session.setCompletedAt(Instant.now());
      }
      sessions.save(session);
    }
  }

  /** Questions for a role; falls back to General when the role has none. */
  List<Question> roleQuestions(String role) {
    List<Question> found = questions.findByRoleOrderBySortOrderAsc(role);
    if (found.isEmpty() && !AppConstants.Job.ROLE_GENERAL.equals(role)) {
      return questions.findByRoleOrderBySortOrderAsc(AppConstants.Job.ROLE_GENERAL);
    }
    return found;
  }

  // ---- Rubric (contract §5, exact formulas) ----

  /** STAR word 3 + >=40 words 3 + >=2 sentences 2 + 40–250 words 2, capped at 10. */
  public int scoreStructure(String answerText) {
    int words = wordCount(answerText);
    int sentences = sentenceCount(answerText);
    String normalized = normalize(answerText);
    int score = 0;
    if (AppConstants.Interview.STAR_WORDS.stream().anyMatch(normalized::contains)) {
      score += 3;
    }
    if (words >= 40) {
      score += 3;
    }
    if (sentences >= 2) {
      score += 2;
    }
    if (words >= 40 && words <= 250) {
      score += 2;
    }
    return Math.min(AppConstants.Interview.MAX_SCORE, score);
  }

  /** round(10 * matched keywords / total keywords). */
  public int scoreRelevance(String answerText, List<String> keywords) {
    if (keywords == null || keywords.isEmpty()) {
      return 0;
    }
    String normalized = normalize(answerText);
    long matched = keywords.stream()
        .filter(k -> !normalize(k).isEmpty() && normalized.contains(normalize(k)))
        .count();
    return (int) Math.round(AppConstants.Interview.MAX_SCORE * (double) matched / keywords.size());
  }

  /** Digit 4 + any job-skill token 3 + >=60 words 3, capped at 10. */
  public int scoreSpecificity(String answerText, List<String> jobSkills) {
    int words = wordCount(answerText);
    String normalized = normalize(answerText);
    int score = 0;
    if (answerText != null && answerText.chars().anyMatch(Character::isDigit)) {
      score += 4;
    }
    if (jobSkills != null && jobSkills.stream()
        .flatMap(skill -> List.of(normalize(skill)
            .split(AppConstants.Matching.WHITESPACE_REGEX)).stream())
        .filter(token -> !token.isEmpty())
        .anyMatch(normalized::contains)) {
      score += 3;
    }
    if (words >= 60) {
      score += 3;
    }
    return Math.min(AppConstants.Interview.MAX_SCORE, score);
  }

  /** 10 for 40–200 words, 7 for 20–39 or 201–300, else 4. */
  public int scoreConciseness(String answerText) {
    int words = wordCount(answerText);
    if (words >= 40 && words <= 200) {
      return AppConstants.Interview.MAX_SCORE;
    }
    if ((words >= 20 && words < 40) || (words > 200 && words <= 300)) {
      return 7;
    }
    return 4;
  }

  /** round(10 * mean of the four dimension scores). */
  public int overallScore(int structure, int relevance, int specificity, int conciseness) {
    return (int) Math.round(AppConstants.Interview.OVERALL_SCALE
        * (structure + relevance + specificity + conciseness) / 4.0 / 10.0);
  }

  /**
   * Feedback, in order: one miss line per dimension below 5, then one line
   * for the strongest dimension (>= 8) or the generic line when none is >= 8.
   * Templates are verbatim from contract §5.
   */
  public List<String> buildFeedback(String answerText, List<String> keywords,
                                    int structure, int relevance,
                                    int specificity, int conciseness) {
    List<String> feedback = new ArrayList<>();
    if (structure < AppConstants.Interview.FEEDBACK_THRESHOLD_LOW) {
      feedback.add(AppConstants.Interview.FEEDBACK_STRUCTURE_MISS);
    }
    if (relevance < AppConstants.Interview.FEEDBACK_THRESHOLD_LOW) {
      List<String> missing = missingKeywords(answerText, keywords);
      feedback.add(AppConstants.Interview.FEEDBACK_RELEVANCE_MISS_PREFIX
          + String.join(", ", missing.subList(0, Math.min(2, missing.size()))) + ".");
    }
    if (specificity < AppConstants.Interview.FEEDBACK_THRESHOLD_LOW) {
      feedback.add(AppConstants.Interview.FEEDBACK_SPECIFICITY_MISS);
    }
    if (conciseness < AppConstants.Interview.FEEDBACK_THRESHOLD_LOW) {
      feedback.add(AppConstants.Interview.FEEDBACK_CONCISENESS_MISS);
    }
    int[] scores = {structure, relevance, specificity, conciseness};
    String[] positives = {
        AppConstants.Interview.FEEDBACK_STRUCTURE_GOOD,
        AppConstants.Interview.FEEDBACK_RELEVANCE_GOOD,
        AppConstants.Interview.FEEDBACK_SPECIFICITY_GOOD,
        AppConstants.Interview.FEEDBACK_CONCISENESS_GOOD};
    int bestIndex = -1;
    for (int i = 0; i < scores.length; i++) {
      if (scores[i] >= AppConstants.Interview.FEEDBACK_THRESHOLD_HIGH
          && (bestIndex < 0 || scores[i] > scores[bestIndex])) {
        bestIndex = i;
      }
    }
    feedback.add(bestIndex >= 0 ? positives[bestIndex] : AppConstants.Interview.FEEDBACK_GENERIC);
    return feedback;
  }

  private List<String> missingKeywords(String answerText, List<String> keywords) {
    if (keywords == null || keywords.isEmpty()) {
      return List.of();
    }
    String normalized = normalize(answerText);
    return keywords.stream()
        .filter(k -> !normalize(k).isEmpty() && !normalized.contains(normalize(k)))
        .toList();
  }

  static int wordCount(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return text.trim().split(AppConstants.Matching.WHITESPACE_REGEX).length;
  }

  /** Count of [.!?] boundaries; at least 1 when the text is non-empty. */
  static int sentenceCount(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    long count = text.chars().filter(c -> c == '.' || c == '!' || c == '?').count();
    return (int) Math.max(1, count);
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.toLowerCase().replaceAll(AppConstants.Matching.WHITESPACE_REGEX, " ").trim();
  }

  private InterviewSession findOwnedSession(UUID id) {
    UUID userId = SecurityUtil.currentUserId();
    return sessions.findById(id)
        .filter(s -> s.getUserId().equals(userId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.SESSION_NOT_FOUND));
  }

  private Job findJob(UUID jobId) {
    return jobs.findById(jobId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.JOB_NOT_FOUND));
  }
}

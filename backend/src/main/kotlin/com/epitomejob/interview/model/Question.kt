package com.epitomejob.interview.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** One seeded interview question. Keywords are comma-separated TEXT. */
@Entity
@Table(name = "questions", schema = "interview")
class Question(
  @Column(nullable = false)
  var role: String = "",

  @Column(nullable = false)
  var category: String = "",

  @Column(nullable = false, columnDefinition = "TEXT")
  var text: String = "",

  @Column(nullable = false)
  var sortOrder: Int = 0
) {

  @Id
  var id: UUID = UUID.randomUUID()

  /**
   * Stored keywords text (comma-separated). The column keeps the Java
   * field's name (`keywords`); the public property exposes the parsed list.
   */
  @Column(name = "keywords", nullable = false, columnDefinition = "TEXT")
  private var keywordsText: String = ""

  var keywords: List<String>
    get() {
      if (keywordsText.isBlank()) return emptyList()
      return keywordsText.split(AppConstants.Job.SKILLS_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    }
    set(value) {
      keywordsText = value.joinToString(AppConstants.Job.SKILLS_SEPARATOR)
    }

  constructor(
    role: String,
    category: String,
    text: String,
    keywordList: List<String>?,
    sortOrder: Int
  ) : this(role, category, text, sortOrder) {
    this.keywords = keywordList ?: emptyList()
  }
}

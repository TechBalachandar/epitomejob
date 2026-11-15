package com.epitomejob.common.security

import com.epitomejob.common.constants.AppConstants
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/** Stateless JWT security. No sessions, no CSRF (nothing cookie-based). */
@Configuration
class SecurityConfig {

  @Bean
  fun passwordEncoder(): PasswordEncoder {
    return BCryptPasswordEncoder(AppConstants.Security.BCRYPT_COST)
  }

  @Bean
  fun filterChain(http: HttpSecurity, jwtFilter: JwtAuthFilter): SecurityFilterChain {
    return http
      .csrf { it.disable() }
      .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
      .authorizeHttpRequests { a ->
        a
          .requestMatchers(
            "/auth/**", "/actuator/health",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
          ).permitAll()
          // Stripe posts the webhook with no JWT — signature is verified in the handler.
          .requestMatchers(HttpMethod.POST, "/billing/webhook").permitAll()
          .requestMatchers("/admin/**").hasRole("ADMIN")
          .anyRequest().authenticated()
      }
      .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
      .build()
  }
}

package com.capstone.fraud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Role model: AGENT captures cases, INVESTIGATOR runs analysis and
 * follow-ups, REVIEWER records dispositions, AUDITOR reads everything.
 * CSRF stays enabled; demo users are synthetic and overridable via env.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService users(PasswordEncoder encoder,
                                    @Value("${capstone.demo-users.agent-password:agent123}") String agentPw,
                                    @Value("${capstone.demo-users.investigator-password:invest123}") String invPw,
                                    @Value("${capstone.demo-users.reviewer-password:review123}") String revPw,
                                    @Value("${capstone.demo-users.auditor-password:audit123}") String audPw) {
        return new InMemoryUserDetailsManager(
                User.withUsername("agent").password(encoder.encode(agentPw)).roles("AGENT").build(),
                User.withUsername("investigator").password(encoder.encode(invPw)).roles("INVESTIGATOR").build(),
                User.withUsername("reviewer").password(encoder.encode(revPw)).roles("REVIEWER").build(),
                User.withUsername("auditor").password(encoder.encode(audPw)).roles("AUDITOR").build(),
                User.withUsername("lead").password(encoder.encode(revPw))
                        .roles("AGENT", "INVESTIGATOR", "REVIEWER", "AUDITOR").build());
    }

    @Bean
    public SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/actuator/health").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/cases").hasAnyRole("AGENT", "INVESTIGATOR")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/cases/*/investigations").hasRole("INVESTIGATOR")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/cases/*/messages").hasRole("INVESTIGATOR")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/cases/*/reviews").hasRole("REVIEWER")
                        .requestMatchers("/cases/new").hasAnyRole("AGENT", "INVESTIGATOR")
                        .requestMatchers("/cases/*/review").hasRole("REVIEWER")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/cases/*/investigate").hasRole("INVESTIGATOR")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/cases/*/messages").hasRole("INVESTIGATOR")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/cases/*/review").hasRole("REVIEWER")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/cases", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                .httpBasic(basic -> {
                });
        return http.build();
    }
}

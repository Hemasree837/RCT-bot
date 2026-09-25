package com.example.resume_chatbot.service;

import com.example.resume_chatbot.model.CourseRecommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class CourseRecommendationService {

    // Repository of verified, authoritative, official educational resources
    private static final Map<String, CourseRecommendation> RESOURCE_CATALOG = new HashMap<>();

    static {
        register("Docker", "Docker Documentation & Hands-on Get Started", "Docker Official",
                "https://docs.docker.com/get-started/",
                "Comprehensive official guide to containerization, images, volumes, and multi-container apps.");

        register("Kubernetes", "Kubernetes Basics & Architecture Tutorial", "Kubernetes.io",
                "https://kubernetes.io/docs/tutorials/kubernetes-basics/",
                "Official interactive walkthrough for deploying, scaling, and updating containerized applications.");

        register("AWS", "AWS Cloud Practitioner Essentials", "AWS Training & Certification",
                "https://aws.amazon.com/training/digital/aws-cloud-practitioner-essentials/",
                "Official free fundamentals course covering core AWS compute, networking, and storage services.");

        register("Azure", "Microsoft Azure Fundamentals (AZ-900) Learning Path", "Microsoft Learn",
                "https://learn.microsoft.com/en-us/training/paths/microsoft-azure-fundamentals-az-900/",
                "Official Microsoft interactive learning modules covering cloud concepts and Azure architecture.");

        register("Spring Boot", "Building an Application with Spring Boot", "Spring Guides (VMware)",
                "https://spring.io/guides/gs/spring-boot",
                "Official Spring tutorial to build production-grade, stand-alone Spring applications.");

        register("REST APIs", "RESTful Web Services Best Practices & Guide", "Spring.io",
                "https://spring.io/guides/gs/rest-service",
                "Official guide to designing, building, and consuming scalable RESTful web APIs.");

        register("Microservices", "Microservices Architecture Pattern Guide", "Microservices.io",
                "https://microservices.io/patterns/microservices.html",
                "Industry-standard architectural blueprints for decomposing monoliths into resilient microservices.");

        register("Git", "Pro Git Book (Free Full Online Edition)", "Git-SCM Official",
                "https://git-scm.com/book/en/v2",
                "The authoritative reference covering branching, merging, rebasing, and remote workflows.");

        register("SQL", "SQL Tutorial and Interactive Database Practice", "W3Schools",
                "https://www.w3schools.com/sql/",
                "Hands-on interactive practice for relational queries, joins, indexes, and transactions.");

        register("PostgreSQL", "PostgreSQL Official Documentation & Tutorial", "PostgreSQL.org",
                "https://www.postgresql.org/docs/current/tutorial.html",
                "The official guide to advanced relational database concepts, indexing, and query optimization.");

        register("Redis", "Redis University - Free Developer Courses", "Redis Official",
                "https://redis.io/learn",
                "Official interactive courses covering in-memory data structures, caching, and pub/sub patterns.");

        register("Kafka", "Apache Kafka Quickstart & Event Streaming Guide", "Apache Software Foundation",
                "https://kafka.apache.org/quickstart",
                "Official guide for building real-time distributed data pipelines and event streaming.");

        register("Python", "The Python Official Tutorial", "Python Software Foundation",
                "https://docs.python.org/3/tutorial/",
                "Official step-by-step tutorial covering Python syntax, data structures, and standard libraries.");

        register("React", "Learn React - Official Interactive Docs", "React Official",
                "https://react.dev/learn",
                "Modern component-driven UI development with hooks, state management, and modern best practices.");

        register("TypeScript", "TypeScript for JavaScript Programmers", "TypeScript Official",
                "https://www.typescriptlang.org/docs/handbook/typescript-in-5-minutes.html",
                "Official handbook introduction to static typing, interfaces, generics, and compiler options.");

        register("Linux", "Linux Journey - Free Interactive Guide", "Linux Journey",
                "https://linuxjourney.com/",
                "Structured guide covering command-line fundamentals, file permissions, processes, and packages.");

        register("CI/CD", "GitHub Actions Documentation & Quickstart", "GitHub",
                "https://docs.github.com/en/actions",
                "Official tutorial to automate building, testing, and continuous deployment workflows.");

        register("System Design", "System Design Primer - Open Source Guide", "GitHub (Donne Martin)",
                "https://github.com/donnemartin/system-design-primer",
                "Comprehensive open-source collection for learning how to design large-scale distributed systems.");

        register("Unit Testing", "JUnit 5 User Guide", "JUnit Team",
                "https://junit.org/junit5/docs/current/user-guide/",
                "Official comprehensive guide to writing unit tests, assertions, and test fixtures in Java.");
    }

    private static void register(String skill, String title, String provider, String url, String description) {
        RESOURCE_CATALOG.put(skill.toLowerCase(), CourseRecommendation.builder()
                .targetSkill(skill)
                .title(title)
                .provider(provider)
                .url(url)
                .description(description)
                .build());
    }

    /**
     * Recommends 1 to 3 authoritative courses/resources matching missing skills.
     */
    public List<CourseRecommendation> recommendCourses(List<String> missingSkills) {
        if (missingSkills == null || missingSkills.isEmpty()) {
            return Collections.singletonList(
                    RESOURCE_CATALOG.get("system design")
            );
        }

        List<CourseRecommendation> recommendations = new ArrayList<>();
        Set<String> addedSkills = new HashSet<>();

        for (String skill : missingSkills) {
            String lowerSkill = skill.toLowerCase();
            CourseRecommendation rec = findRecommendationForSkill(lowerSkill);
            if (rec != null && addedSkills.add(rec.getTargetSkill())) {
                recommendations.add(rec);
            }
            if (recommendations.size() >= 3) {
                break;
            }
        }

        // If fewer than 1 found, provide general engineering recommendations
        if (recommendations.isEmpty()) {
            recommendations.add(RESOURCE_CATALOG.get("git"));
            recommendations.add(RESOURCE_CATALOG.get("system design"));
        }

        return recommendations;
    }

    private CourseRecommendation findRecommendationForSkill(String lowerSkill) {
        if (RESOURCE_CATALOG.containsKey(lowerSkill)) {
            return RESOURCE_CATALOG.get(lowerSkill);
        }

        for (Map.Entry<String, CourseRecommendation> entry : RESOURCE_CATALOG.entrySet()) {
            if (lowerSkill.contains(entry.getKey()) || entry.getKey().contains(lowerSkill)) {
                return entry.getValue();
            }
        }
        return null;
    }
}

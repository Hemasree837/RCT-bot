# 🚀 Resume Career Assistant & ATS Analyzer Bot

> An AI-powered Telegram Bot and Spring Boot backend that analyzes resumes against target job descriptions, computes transparent ATS-like compatibility scores, pinpoints skill gaps, suggests verified official learning resources, compares multiple resumes, and answers career questions.

---

## 📌 Architecture & Design (Interview Guide for B.Tech Students)

```
                           +------------------------+
                           |  Telegram Mobile App   |
                           +-----------+------------+
                                       |
                   (Long-polling / HTTPS via Bot API)
                                       |
                                       v
                     +------------------------------------+
                     |         Telegram Bot Layer         |
                     |  • TelegramBot (Polling Daemon)    |
                     |  • TelegramUpdateHandler (FSM)     |
                     |  • TelegramMessageService (HTML)   |
                     |  • TelegramFileService (In-Memory) |
                     +-----------------+------------------+
                                       |
                   +-------------------+-------------------+
                   |                                       |
                   v                                       v
    +------------------------------+     +-----------------------------------+
    |    REST Controller Layer     |     |       Core Service Layer          |
    |  • POST /api/resume/analyze  |     |  • ResumeParserService (PDFBox)   |
    |  • POST /api/resume/compare  |     |  • SkillMatchingService (Regex)   |
    |  • POST /api/resume/skills   |     |  • ATSScoreService (Weighted Fmla)|
    |  • POST /api/resume/courses  |     |  • CourseRecommendationService    |
    |  • POST /api/resume/chat     |     |  • ResumeComparisonService        |
    +------------------------------+     +-----------------+-----------------+
                                                           |
                                         +-----------------+-----------------+
                                         |                                   |
                                         v                                   v
                          +-----------------------------+     +-----------------------------+
                          |      AIService (LLM)        |     |  Deterministic Advisor      |
                          | (Gemini / OpenAI API via    |     | (Rule-based expert system   |
                          |  Spring RestClient)         |     |  for zero-downtime offline) |
                          +-----------------------------+     +-----------------------------+
```

### 🧩 Layered Software Architecture
The application adheres strictly to standard **Layered Architecture** principles:
1. **Telegram Layer (`telegram/`)**: Encapsulates all interactions with the Telegram Bot API (long-polling lifecycle, conversation state transitions, document download, HTML rendering, inline/reply keyboards).
2. **Controller Layer (`controller/`)**: Provides decoupled REST endpoints (`/api/resume/...`) enabling easy automated testing, third-party integrations, and web dashboard extensibility.
3. **Service Layer (`service/`)**: Houses business logic (parsing, normalization, skill matching, weighted ATS scoring, course recommendation catalog, multi-resume comparison).
4. **Model Layer (`model/`)**: Clean domain models (`Resume`, `JobDescription`, `SkillMatch`, `ScoreBreakdown`, `ResumeAnalysis`, `UserSession`, `UserState`).
5. **Config Layer (`config/`)**: Type-safe configuration properties loaded securely from environment variables.

---

## 🎯 Key Design Patterns Used

1. **State Machine Pattern (`UserState`, `UserSession`, `TelegramUpdateHandler`)**:
   - Manages user progress through conversation states (`IDLE` ➔ `AWAITING_RESUME_FOR_ANALYSIS` ➔ `AWAITING_JOB_DESCRIPTION` ➔ `AWAITING_QUESTION`).
   - Ensures commands like `/cancel` or `/start` safely reset user context at any point.
2. **Strategy / Provider Abstraction Pattern (`AIService`, `AIServiceImpl`)**:
   - The application does not hardcode a single AI vendor.
   - When an external API key (`AI_API_KEY`) is provided, it calls Gemini / OpenAI via Spring `RestClient`.
   - If no key is set or the external service experiences network timeouts, it seamlessly falls back to the built-in deterministic career advisor engine.
3. **Builder Pattern (Lombok `@Builder`)**:
   - Used across all models (`Resume`, `ResumeAnalysis`, `ScoreBreakdown`) for clean, immutable construction.

---

## 📊 ATS-like Compatibility Scoring Formula

> **Disclaimer:** This score is an application-generated compatibility estimate calculated using weighted heuristic factors. It is NOT an official proprietary score from any commercial ATS vendor.

| Factor | Weight | Evaluation Criteria |
| :--- | :---: | :--- |
| **Skill Match** | **50%** | Ratio of matched technical skills to required job description skills (`(matched / total_req) * 50`). |
| **Keyword Alignment** | **20%** | Density and alignment of role keywords (e.g. backend, scalability, microservices, databases) in resume text. |
| **Experience & Projects** | **15%** | Presence of internships, production projects, and measurable impact metrics (percentages, numbers, latency improvements). |
| **Education Relevance** | **10%** | Detection of relevant engineering degrees (B.Tech, B.E., M.Tech, MCA, Computer Science). |
| **Readability & Completeness** | **5%** | Presence of contact info, GitHub, LinkedIn, clear sections. |
| **Total Compatibility Score** | **100%** | Sum rounded to integer `[0 – 100]`. |

---

## 🌐 Webhook vs Long Polling

In this project, **Long Polling** is chosen for local development:
- **Why Long Polling?** Telegram Bot API supports `getUpdates` with long-polling. It works directly behind home routers, university firewalls, and local NAT without requiring a public static IP, SSL certificate, or tunneling tools like ngrok.
- **Webhook Readiness:** The bot logic is separated cleanly in `TelegramUpdateHandler.processUpdate(Map<String, Object>)`. In a cloud production environment (e.g., AWS EC2, GCP Cloud Run), a simple `@PostMapping("/api/telegram/webhook")` endpoint can delegate directly to `processUpdate(...)` without modifying business logic.

---

## 🛠️ Security Best Practices

- **Zero Hardcoded Secrets:** Telegram bot tokens, AI API keys, and passwords are never hardcoded in Java code.
- **Environment Variables:** All credentials are read from environment variables (`TELEGRAM_BOT_TOKEN`, `AI_API_KEY`).
- **Git Protection:** `.gitignore` excludes `.env`, `application-local.properties`, `secrets/`, and temporary upload directories.
- **Safe In-Memory File Handling:** PDF resumes uploaded via Telegram are processed directly in-memory as streams (`InputStream`). Files are never permanently written to disk or exposed to other users.
- **File Validation:** Enforces a 10MB maximum file size and validates PDF format headers to prevent corrupted or malicious uploads.

---

## 🚀 How to Run the Application

### Prerequisites
- **Java JDK 21** or higher
- **Maven** (included via `./mvnw.cmd` / `./mvnw`)
- A **Telegram Bot Token** from [@BotFather](https://t.me/BotFather) on Telegram

### Step 1: Obtain a Telegram Bot Token
1. Open Telegram and search for [@BotFather](https://t.me/BotFather).
2. Send `/newbot` and follow the prompts to choose a name and username (e.g. `MyCareerBot`).
3. Copy the HTTP API token provided by BotFather (e.g. `123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ`).

### Step 2: Configure Environment Variables
In your terminal (PowerShell / Command Prompt):

```powershell
# Set your Telegram Bot Token
$env:TELEGRAM_BOT_TOKEN="your-telegram-bot-token-here"

# (Optional) Set your AI API Key if using Gemini
$env:AI_PROVIDER="mock"   # or "gemini"
$env:AI_API_KEY="your-gemini-api-key-here"
```

*Alternatively, copy `src/main/resources/application-example.properties` to `src/main/resources/application-local.properties` and fill in your values.*

### Step 3: Run the Application

```powershell
cd resume-chatbot
.\mvnw.cmd spring-boot:run
```

Once started:
- If `TELEGRAM_BOT_TOKEN` is set, the bot will log:
  `Starting Telegram Bot long-polling daemon for bot @YourBotUsername...`
- If running without a token, the bot will start in **Standby / REST API mode**, allowing full testing via HTTP endpoints!

---

## 📱 Telegram User Interaction Flow

1. Open Telegram, search for your bot, and send `/start`.
2. Tap **[📄 Analyze Resume]**.
3. Attach and send your **PDF resume**.
4. When prompted, reply with your target role (e.g. `Java Backend Developer`) or paste a complete job description.
5. The bot generates:
   - ATS-like Compatibility Score (`78/100`) with factor breakdown.
   - Matched Skills list (`✓ Java`, `✓ Spring Boot`, `✓ SQL`).
   - Missing Skills list (`• Docker`, `• AWS`, `• Microservices`).
   - Actionable Resume Suggestions.
   - 1–3 Verified Official Learning Links (Docker docs, AWS training, etc.).
6. Tap **[💬 Ask a Question]** to ask follow-up questions:
   - *"What should I learn first?"*
   - *"Why is my score 78?"*
   - *"What projects should I highlight for this role?"*
7. Tap **[📊 Compare Resumes]** to upload multiple resumes and receive an objective side-by-side comparison.

---

## 📡 REST API Reference

| Endpoint | Method | Content-Type | Description |
| :--- | :---: | :---: | :--- |
| `/api/resume/analyze` | `POST` | `multipart/form-data` | Upload PDF file + `jobDescription` string to get full analysis JSON. |
| `/api/resume/compare` | `POST` | `multipart/form-data` | Upload multiple PDF files (`files`) + `jobDescription` to compare resumes. |
| `/api/resume/skills` | `POST` | `application/json` | Match skills list against a job description. |
| `/api/resume/courses` | `POST` | `application/json` | Get verified learning recommendations for missing skills. |
| `/api/resume/chat` | `POST` | `application/json` | Ask a career/resume question with session context. |

### Sample cURL Requests

**Analyze Skills:**
```bash
curl -X POST http://localhost:8080/api/resume/skills \
  -H "Content-Type: application/json" \
  -d '{"skills": ["Java", "Spring Boot", "SQL"], "jobDescription": "Java Backend Developer with Docker and AWS"}'
```

**Get Course Recommendations:**
```bash
curl -X POST http://localhost:8080/api/resume/courses \
  -H "Content-Type: application/json" \
  -d '{"skills": ["Docker", "Kubernetes"]}'
```

**Ask Career Chatbot:**
```bash
curl -X POST http://localhost:8080/api/resume/chat \
  -H "Content-Type: application/json" \
  -d '{"chatId": 101, "question": "What projects should I build for a Java Backend role?"}'
```

---

## 🧪 Testing

Run the full automated test suite:

```powershell
.\mvnw.cmd test
```

Unit and integration tests cover:
- PDF parsing and regex section segmentation (`ResumeParserServiceTest`)
- Skill matching, boundary protection (e.g. `Java` != `JavaScript`), and role parsing (`SkillMatchingServiceTest`)
- ATS-like weighted scoring calculation and factor validation (`ATSScoreServiceTest`)
- Official course recommendations and hyperlink validity (`CourseRecommendationServiceTest`)
- Objective multi-resume comparison without subjective bias (`ResumeComparisonServiceTest`)
- REST API controller endpoints (`ResumeApiControllerTest`)

---

## 🎓 Interview Talking Points (For Final-Year Students)

When explaining this project in a technical interview:
1. **Explain the Real-World Problem:** Job applicants struggle to know if their resume aligns with job requirements before submitting applications. This bot provides instant, transparent, and actionable feedback directly inside Telegram.
2. **Explain the Architecture:** "I separated the application into a Telegram presentation layer, a decoupled REST API controller layer, and an independent domain service layer. This ensures that changing the frontend from Telegram to a Web UI requires zero modifications to the core analysis engine."
3. **Explain Text Extraction & NLP Matching:** "I used Apache PDFBox to stream and extract raw text from PDF resumes. For skill matching, I used word-boundary regex patterns to avoid false positives (e.g., ensuring `Java` doesn't falsely match `JavaScript`)."
4. **Explain Pluggable AI Design:** "I used the Strategy Pattern to abstract `AIService`. If an API key is available, it communicates with Gemini/OpenAI via Spring `RestClient`. If the external AI service is unreachable, the application falls back to a deterministic rule-based career advisor engine, ensuring zero downtime."
5. **Security & Privacy:** "Resumes are processed purely in-memory as streams and are never persisted to disk, protecting candidate privacy. All secrets are managed strictly through environment variables."

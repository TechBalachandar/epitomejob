CREATE SCHEMA IF NOT EXISTS job;
CREATE TABLE job.jobs (
  id UUID PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  company VARCHAR(120) NOT NULL,
  location VARCHAR(120) NOT NULL,
  work_type VARCHAR(20) NOT NULL,
  salary_min INT,
  salary_max INT,
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  sponsors_h1b BOOLEAN NOT NULL DEFAULT FALSE,
  skills TEXT NOT NULL DEFAULT '',
  summary TEXT,
  role VARCHAR(20) NOT NULL,
  posted_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_jobs_role ON job.jobs (role);
CREATE INDEX idx_jobs_posted ON job.jobs (posted_at);

CREATE SCHEMA IF NOT EXISTS tracker;
CREATE TABLE tracker.entries (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  job_id UUID NOT NULL,
  resume_id UUID,
  status VARCHAR(20) NOT NULL DEFAULT 'SAVED',
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_tracker_user_job UNIQUE (user_id, job_id)
);
CREATE INDEX idx_tracker_user ON tracker.entries (user_id, created_at DESC);

CREATE SCHEMA IF NOT EXISTS interview;
CREATE TABLE interview.questions (
  id UUID PRIMARY KEY,
  role VARCHAR(20) NOT NULL,
  category VARCHAR(30) NOT NULL,
  text TEXT NOT NULL,
  keywords TEXT NOT NULL DEFAULT '',
  sort_order INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_questions_role ON interview.questions (role, sort_order);
CREATE TABLE interview.sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  job_id UUID NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
CREATE INDEX idx_sessions_user ON interview.sessions (user_id, created_at DESC);
CREATE TABLE interview.answers (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL,
  question_id UUID NOT NULL,
  answer_text TEXT NOT NULL,
  structure INT NOT NULL DEFAULT 0,
  relevance INT NOT NULL DEFAULT 0,
  specificity INT NOT NULL DEFAULT 0,
  conciseness INT NOT NULL DEFAULT 0,
  overall_score INT NOT NULL DEFAULT 0,
  feedback TEXT NOT NULL DEFAULT '',
  CONSTRAINT uq_answer_session_question UNIQUE (session_id, question_id)
);
CREATE INDEX idx_answers_session ON interview.answers (session_id);

-- Seed jobs: 18, roles Backend (4), AI (4), Android (2), Platform (3), ML (3), General (2).
INSERT INTO job.jobs (id, title, company, location, work_type, salary_min, salary_max, currency, sponsors_h1b, skills, summary, role, posted_at) VALUES
('10000000-0000-4000-8000-000000000001', 'Senior Software Engineer, Backend (Java)', 'Northwind Health Systems', 'Reston, VA', 'Hybrid', 145000, 175000, 'USD', TRUE, 'Java 21,Spring Boot,Kafka,PostgreSQL', 'Build core banking APIs for online and mobile banking on a Java and Spring Boot platform.', 'Backend', '2026-11-10 09:00:00+00'),
('10000000-0000-4000-8000-000000000002', 'Staff Backend Engineer', 'Capital One', 'McLean, VA', 'Hybrid', 170000, 210000, 'USD', TRUE, 'Java,Kotlin,Spring Boot,AWS', 'Lead backend services for card products, mentoring engineers and driving API design.', 'Backend', '2026-11-09 09:00:00+00'),
('10000000-0000-4000-8000-000000000003', 'Java Backend Developer', 'Leidos', 'Reston, VA', 'Onsite', 120000, 150000, 'USD', FALSE, 'Java 17,Spring Boot,Oracle,SQL', 'Develop and maintain backend services for federal health programs.', 'Backend', '2026-11-05 09:00:00+00'),
('10000000-0000-4000-8000-000000000004', 'Principal Software Engineer, Backend', 'Freddie Mac', 'McLean, VA', 'Hybrid', 180000, 220000, 'USD', TRUE, 'Java,Spring Boot,Microservices,Kafka', 'Own backend architecture for mortgage servicing platforms at enterprise scale.', 'Backend', '2026-11-11 09:00:00+00'),
('10000000-0000-4000-8000-000000000005', 'Senior Agentic AI Engineer', 'Deloitte', 'Rosslyn, VA', 'Hybrid', 150000, 200000, 'USD', TRUE, 'Python,LangGraph,LangChain,Azure', 'Build reusable, secure, human-governed agentic AI for healthcare clients.', 'AI', '2026-11-08 09:00:00+00'),
('10000000-0000-4000-8000-000000000006', 'AI Engineer (LLM Applications)', 'Booz Allen Hamilton', 'Washington, DC', 'Hybrid', 140000, 180000, 'USD', TRUE, 'Python,RAG,Vector Search,Azure AI Search', 'Ship LLM features with retrieval, evaluation, and guardrails for federal clients.', 'AI', '2026-11-07 09:00:00+00'),
('10000000-0000-4000-8000-000000000007', 'Generative AI Engineer', 'SAIC', 'Reston, VA', 'Remote', 135000, 175000, 'USD', FALSE, 'Python,LangChain,Prompt Engineering', 'Prototype generative AI assistants for internal tools and customer support.', 'AI', '2026-11-06 09:00:00+00'),
('10000000-0000-4000-8000-000000000008', 'Senior AI Platform Engineer', 'Peraton', 'Arlington, VA', 'Onsite', 160000, 200000, 'USD', TRUE, 'Python,Temporal,MCP,Kubernetes', 'Run the platform that hosts agentic workflows, MCP servers, and model services.', 'AI', '2026-11-04 09:00:00+00'),
('10000000-0000-4000-8000-000000000009', 'Senior Android Engineer', 'Navy Federal Credit Union', 'Vienna, VA', 'Hybrid', 140000, 175000, 'USD', TRUE, 'Kotlin,Android,Jetpack Compose', 'Lead Android development for mobile banking used by millions of members.', 'Android', '2026-11-10 09:00:00+00'),
('10000000-0000-4000-8000-000000000010', 'Android Developer', 'General Dynamics', 'Fairfax, VA', 'Onsite', 125000, 155000, 'USD', FALSE, 'Kotlin,Java,Android SDK', 'Build secure Android apps for defense and government users.', 'Android', '2026-11-03 09:00:00+00'),
('10000000-0000-4000-8000-000000000011', 'Senior Platform Engineer', 'MITRE', 'McLean, VA', 'Hybrid', 150000, 190000, 'USD', TRUE, 'Kubernetes,Azure,Terraform', 'Build shared platform services that product teams deploy onto with confidence.', 'Platform', '2026-11-09 09:00:00+00'),
('10000000-0000-4000-8000-000000000012', 'Cloud Platform Engineer', 'CGI', 'Washington, DC', 'Remote', 130000, 165000, 'USD', TRUE, 'AWS,Azure,Docker,CI/CD', 'Automate cloud infrastructure and delivery pipelines for client platforms.', 'Platform', '2026-11-02 09:00:00+00'),
('10000000-0000-4000-8000-000000000013', 'DevOps Platform Engineer', 'Accenture', 'Arlington, VA', 'Remote', 125000, 160000, 'USD', FALSE, 'Kubernetes,Docker,Jenkins', 'Keep delivery pipelines fast and reliable across many client teams.', 'Platform', '2026-11-05 09:00:00+00'),
('10000000-0000-4000-8000-000000000014', 'Senior Machine Learning Engineer', 'Amazon', 'Arlington, VA', 'Hybrid', 165000, 210000, 'USD', TRUE, 'Python,PyTorch,TensorFlow,AWS', 'Train and serve ML models that power recommendations at massive scale.', 'ML', '2026-11-08 09:00:00+00'),
('10000000-0000-4000-8000-000000000015', 'ML Engineer (NLP)', 'Microsoft', 'Washington, DC', 'Remote', 150000, 195000, 'USD', TRUE, 'Python,NLP,Transformers,Azure', 'Build NLP systems for document understanding and search.', 'ML', '2026-11-07 09:00:00+00'),
('10000000-0000-4000-8000-000000000016', 'Machine Learning Engineer', 'Infosys', 'Reston, VA', 'Onsite', 120000, 155000, 'USD', FALSE, 'Python,Scikit-learn,SQL', 'Develop ML models for client analytics and forecasting use cases.', 'ML', '2026-11-03 09:00:00+00'),
('10000000-0000-4000-8000-000000000017', 'Senior Software Engineer', 'Meta', 'Washington, DC', 'Hybrid', 175000, 220000, 'USD', TRUE, 'Python,Java,Distributed Systems', 'Build distributed systems that serve products used by billions of people.', 'General', '2026-11-11 09:00:00+00'),
('10000000-0000-4000-8000-000000000018', 'Software Engineer (Full Stack)', 'Google', 'Washington, DC', 'Remote', 145000, 190000, 'USD', TRUE, 'Java,Python,React,Cloud', 'Ship full-stack features across web services and user-facing products.', 'General', '2026-11-06 09:00:00+00');

-- Question bank: 5 per role x 6 roles = 30.
INSERT INTO interview.questions (id, role, category, text, keywords, sort_order) VALUES
('20000000-0000-4000-8000-000000000001', 'Backend', 'Technical', 'Explain how you would design a REST API for a banking service that must handle 50 requests per second with OAuth 2.0 authentication.', 'REST,OAuth,authentication,rate limiting,banking', 1),
('20000000-0000-4000-8000-000000000002', 'Backend', 'Technical', 'How do you handle database transactions across multiple microservices without distributed transactions?', 'transaction,saga,eventual consistency,idempotency,microservices', 2),
('20000000-0000-4000-8000-000000000003', 'Backend', 'System Design', 'Design a notification service that delivers account alerts within seconds to millions of users.', 'queue,Kafka,scalability,fan-out,delivery', 3),
('20000000-0000-4000-8000-000000000004', 'Backend', 'Behavioral', 'Tell me about a time you debugged a production issue under pressure. What was the outcome?', 'situation,action,result,debugging,production', 4),
('20000000-0000-4000-8000-000000000005', 'Backend', 'Role-specific', 'How would you secure a Spring Boot API against SQL injection and broken authentication?', 'Spring Boot,SQL injection,authentication,validation,security', 5),
('20000000-0000-4000-8000-000000000006', 'AI', 'Technical', 'Explain retrieval-augmented generation (RAG) and when you would use it over fine-tuning.', 'retrieval,embeddings,vector search,fine-tuning,grounding', 1),
('20000000-0000-4000-8000-000000000007', 'AI', 'Technical', 'How do you evaluate an LLM agent before shipping it to production?', 'evaluation,benchmarks,hallucination,guardrails,metrics', 2),
('20000000-0000-4000-8000-000000000008', 'AI', 'System Design', 'Design an agentic workflow that triages support tickets and escalates the risky ones to a human.', 'agent,human-in-the-loop,escalation,tools,workflow', 3),
('20000000-0000-4000-8000-000000000009', 'AI', 'Behavioral', 'Describe a time you shipped an AI feature that behaved unexpectedly. What did you learn?', 'situation,action,result,learned,monitoring', 4),
('20000000-0000-4000-8000-000000000010', 'AI', 'Role-specific', 'How do you keep PHI and PII safe when an LLM processes healthcare data?', 'PHI,PII,HIPAA,redaction,encryption', 5),
('20000000-0000-4000-8000-000000000011', 'Android', 'Technical', 'Explain how Jetpack Compose state management differs from the old View-based UI.', 'Compose,state,recomposition,ViewModel,UI', 1),
('20000000-0000-4000-8000-000000000012', 'Android', 'Technical', 'How do you securely store auth tokens on Android?', 'EncryptedSharedPreferences,Keystore,token,refresh,security', 2),
('20000000-0000-4000-8000-000000000013', 'Android', 'System Design', 'Design offline-first sync for a banking app that must work with flaky connectivity.', 'offline,sync,Room,conflict,retry', 3),
('20000000-0000-4000-8000-000000000014', 'Android', 'Behavioral', 'Tell me about a time you fixed a crash that only happened on one device model.', 'situation,action,result,crash,debugging', 4),
('20000000-0000-4000-8000-000000000015', 'Android', 'Role-specific', 'How do you keep an Android app responsive when a network call is slow?', 'coroutines,threading,timeout,caching,UI', 5),
('20000000-0000-4000-8000-000000000016', 'Platform', 'Technical', 'Explain the difference between horizontal and vertical scaling and when each wins.', 'scaling,load balancer,stateless,capacity,Kubernetes', 1),
('20000000-0000-4000-8000-000000000017', 'Platform', 'Technical', 'How do you design a CI/CD pipeline with safe rollbacks?', 'CI/CD,rollback,canary,testing,deployment', 2),
('20000000-0000-4000-8000-000000000018', 'Platform', 'System Design', 'Design a platform that lets ten teams deploy services independently without breaking each other.', 'platform,isolation,versioning,contracts,observability', 3),
('20000000-0000-4000-8000-000000000019', 'Platform', 'Behavioral', 'Describe a time an incident taught your team to change how it deploys.', 'situation,action,result,incident,learned', 4),
('20000000-0000-4000-8000-000000000020', 'Platform', 'Role-specific', 'How do you right-size cloud costs without hurting reliability?', 'cost,autoscaling,monitoring,Reserved,efficiency', 5),
('20000000-0000-4000-8000-000000000021', 'ML', 'Technical', 'Explain the bias-variance tradeoff in plain language with an example.', 'bias,variance,overfitting,generalization,training', 1),
('20000000-0000-4000-8000-000000000022', 'ML', 'Technical', 'How do you detect and fix data drift in a production model?', 'drift,monitoring,retraining,features,metrics', 2),
('20000000-0000-4000-8000-000000000023', 'ML', 'System Design', 'Design a feature store for models that need fresh features at serving time.', 'feature store,serving,latency,versioning,pipeline', 3),
('20000000-0000-4000-8000-000000000024', 'ML', 'Behavioral', 'Tell me about a model you shipped that underperformed in production. What happened next?', 'situation,action,result,monitoring,learned', 4),
('20000000-0000-4000-8000-000000000025', 'ML', 'Role-specific', 'How do you explain a model prediction to a non-technical stakeholder?', 'explainability,SHAP,features,trust,communication', 5),
('20000000-0000-4000-8000-000000000026', 'General', 'Behavioral', 'Tell me about a time you had to learn a new technology quickly to deliver a project.', 'situation,action,result,learned,delivery', 1),
('20000000-0000-4000-8000-000000000027', 'General', 'Behavioral', 'Describe a disagreement with a teammate and how you resolved it.', 'situation,action,result,communication,compromise', 2),
('20000000-0000-4000-8000-000000000028', 'General', 'Technical', 'How do you decide between building a feature and buying a tool that already does it?', 'build,buy,cost,maintenance,tradeoff', 3),
('20000000-0000-4000-8000-000000000029', 'General', 'Technical', 'Walk me through how you debug a failing test in CI that passes on your machine.', 'CI,environment,logs,reproduction,debugging', 4),
('20000000-0000-4000-8000-000000000030', 'General', 'Role-specific', 'Why are you interested in this role, and what would you focus on in your first 90 days?', 'motivation,goals,learning,impact,team', 5);

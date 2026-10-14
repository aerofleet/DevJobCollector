// Values are public search URL parameters; keep existing values compatible.
export const JOB_ROLE_GROUPS = [
  { label: '웹·앱 개발', options: [
    ['backend', '백엔드'], ['frontend', '프론트엔드'], ['fullstack', '풀스택'],
    ['mobile', '모바일'], ['android', 'Android'], ['ios', 'iOS'],
    ['cross-platform', '크로스플랫폼 앱'], ['web-publishing', '웹 퍼블리싱'],
  ] },
  { label: '데이터·인공지능', options: [
    ['data-ai', '데이터·AI 전체'], ['data-engineer', '데이터 엔지니어'],
    ['data-analyst', '데이터 분석가'], ['data-scientist', '데이터 사이언티스트'],
    ['ml-engineer', '머신러닝 엔지니어'], ['ai-engineer', 'AI 엔지니어'],
    ['mlops', 'MLOps'], ['computer-vision', '컴퓨터 비전'], ['nlp', '자연어 처리·LLM'],
  ] },
  { label: '인프라·보안', options: [
    ['devops-security', 'DevOps·보안 전체'], ['devops', 'DevOps'], ['sre', 'SRE'],
    ['cloud', '클라우드'], ['infrastructure', '시스템·인프라'], ['network', '네트워크'],
    ['security', '정보보안'], ['dba', 'DBA'], ['platform', '플랫폼 엔지니어'],
  ] },
  { label: '게임·시스템 개발', options: [
    ['game', '게임 개발'], ['embedded', '임베디드·펌웨어'], ['robotics', '로보틱스'],
    ['iot', 'IoT'], ['blockchain', '블록체인'], ['desktop', '데스크톱 앱'],
  ] },
  { label: '품질·설계·제품', options: [
    ['qa', 'QA·테스트'], ['architect', '소프트웨어 아키텍트'],
    ['engineering-manager', '개발 리드·매니저'], ['product-manager', '프로덕트 매니저·기획'],
    ['product-designer', 'UX·UI 디자인'], ['technical-support', '기술지원·솔루션'],
  ] },
];
export const JOB_ROLES = [
  { value: '', label: '전체 직무' },
  ...JOB_ROLE_GROUPS.flatMap(group => group.options.map(([value, label]) => ({ value, label }))),
];
export const QUICK_JOB_ROLES = JOB_ROLES.filter(role =>
  ['', 'backend', 'frontend', 'fullstack', 'mobile', 'data-ai', 'devops-security'].includes(role.value));

export const TECH_STACK_GROUPS = [
  { label: '프로그래밍 언어', options: ['Java', 'JavaScript', 'TypeScript', 'Python', 'Kotlin', 'Swift', 'Go', 'Rust', 'C', 'C++', 'C#', 'PHP', 'Ruby', 'Scala', 'Dart', 'R', 'Objective-C', 'Elixir', 'Erlang', 'Lua', 'Perl', 'Haskell', 'Clojure', 'Groovy', 'SQL', 'Bash', 'PowerShell'] },
  { label: '웹 프론트엔드', options: ['React', 'Next.js', 'Vue.js', 'Nuxt.js', 'Angular', 'Svelte', 'SvelteKit', 'HTML', 'CSS', 'Sass', 'Tailwind CSS', 'Bootstrap', 'jQuery', 'Redux', 'Zustand', 'Recoil', 'TanStack Query', 'Vite', 'Webpack', 'Storybook', 'Astro'] },
  { label: '백엔드·API', options: ['Spring', 'Spring Boot', 'Spring Security', 'Spring Batch', 'JPA', 'Hibernate', 'MyBatis', 'Node.js', 'Express', 'NestJS', 'Django', 'FastAPI', 'Flask', 'Ruby on Rails', 'Laravel', 'ASP.NET Core', '.NET', 'Gin', 'Ktor', 'GraphQL', 'gRPC'] },
  { label: '모바일·게임·임베디드', options: ['Android', 'iOS', 'Flutter', 'React Native', 'Jetpack Compose', 'SwiftUI', 'Unity', 'Unreal Engine', 'Cocos2d', 'Qt', 'Electron', 'Tauri', 'ROS', 'ROS2', 'FreeRTOS', 'Arduino'] },
  { label: '데이터베이스·검색', options: ['MySQL', 'PostgreSQL', 'MariaDB', 'Oracle', 'SQL Server', 'SQLite', 'MongoDB', 'Redis', 'Elasticsearch', 'OpenSearch', 'DynamoDB', 'Cassandra', 'Couchbase', 'Neo4j', 'ClickHouse', 'InfluxDB', 'Supabase', 'Firebase'] },
  { label: '데이터·AI·분석', options: ['PyTorch', 'TensorFlow', 'Keras', 'scikit-learn', 'Pandas', 'NumPy', 'SciPy', 'OpenCV', 'Hugging Face', 'Transformers', 'LangChain', 'LlamaIndex', 'Spark', 'Hadoop', 'Flink', 'Airflow', 'dbt', 'Databricks', 'Snowflake', 'BigQuery', 'Redshift', 'MLflow', 'Kubeflow', 'Ray', 'ONNX', 'TensorRT', 'CUDA', 'Jupyter', 'Tableau', 'Power BI'] },
  { label: '클라우드·인프라·운영', options: ['AWS', 'Azure', 'GCP', 'OCI', 'Naver Cloud', 'Docker', 'Kubernetes', 'Helm', 'Terraform', 'Ansible', 'Pulumi', 'Packer', 'Linux', 'Unix', 'Nginx', 'Apache', 'Tomcat', 'Kafka', 'RabbitMQ', 'ActiveMQ', 'Istio', 'Envoy', 'Prometheus', 'Grafana', 'Datadog', 'New Relic', 'Sentry', 'OpenTelemetry', 'ELK', 'Vault'] },
  { label: '협업·테스트·CI/CD', options: ['Git', 'GitHub', 'GitLab', 'GitHub Actions', 'GitLab CI', 'Jenkins', 'Argo CD', 'CircleCI', 'Bamboo', 'JUnit', 'Mockito', 'Testcontainers', 'Jest', 'Vitest', 'Cypress', 'Playwright', 'Selenium', 'Appium', 'pytest', 'JMeter', 'k6', 'Postman', 'Swagger', 'Maven', 'Gradle', 'Figma', 'Jira'] },
  { label: '블록체인', options: ['Solidity', 'Ethereum', 'Web3.js', 'Ethers.js', 'Hardhat', 'Hyperledger Fabric'] },
];

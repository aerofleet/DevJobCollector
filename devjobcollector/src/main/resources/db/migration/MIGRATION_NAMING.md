
# DJC Flyway Naming Convention

- Format: `V<version>__djc_<description>.sql`
- Example:
  - `V1__djc_initial_schema.sql`
  - `V2__djc_add_personal_member_signup.sql`
  - `V3__djc_add_job_post_indexes.sql`

Notes:
- 이 디렉터리에는 DevJobCollector 스키마 변경만 둔다.
- 버전은 `devjob` 데이터베이스 기준으로 반드시 증가시킨다.
- 설명은 소문자와 밑줄을 사용한다.
- 운영에서 적용된 파일은 수정하지 않고 새 버전을 추가한다.
- 기존 비어 있지 않은 운영 DB는 V1으로 baseline하고 V2부터 실행한다.

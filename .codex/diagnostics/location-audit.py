import base64
import json
import os
import shutil
import subprocess
import sys
from urllib.parse import urlsplit

# Read-only, account-scoped diagnosis. Never emit credentials or resume contents.
email = base64.b64decode(sys.argv[1], validate=True).decode('utf-8')
containers = subprocess.check_output(['docker', 'ps', '-q', '--filter', 'label=com.docker.compose.project=djc',
                                      '--filter', 'label=com.docker.compose.service=app'], text=True).split()
if len(containers) != 1:
    raise SystemExit('Expected exactly one active DJC application')
app = json.loads(subprocess.check_output(['docker', 'inspect', containers[0]], text=True))[0]
env = dict(item.split('=', 1) for item in app['Config']['Env'] if '=' in item)
url = urlsplit(env['SPRING_DATASOURCE_URL'].removeprefix('jdbc:'))
print('runtime_image=' + app['Config']['Image'], flush=True)
identity = "CONVERT(0x" + email.encode('utf-8').hex() + " USING utf8mb4) COLLATE utf8mb4_unicode_ci"
query = f"""
START TRANSACTION READ ONLY;
SELECT a.id,
 JSON_EXTRACT(a.result_json,'$.derivedPreferences.careerYears') career_years,
 JSON_KEYS(JSON_EXTRACT(a.result_json,'$.review.skillEvidence')) grounded_skill_names,
 JSON_LENGTH(JSON_EXTRACT(a.result_json,'$.aiReview.positioningEvidence')) positioning_evidence_count,
 JSON_LENGTH(JSON_EXTRACT(a.result_json,'$.sources')) attachment_source_count
 FROM ai_analysis_request a WHERE a.resume_id=1 ORDER BY a.id DESC LIMIT 1;
SELECT e.idx,JSON_KEYS(e.item) experience_fields,
 CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(e.item,'$.description'))) description_chars,
 JSON_EXTRACT(e.item,'$.workType') work_type,
 JSON_EXTRACT(e.item,'$.isCurrent') is_current,
 JSON_EXTRACT(e.item,'$.startDate') start_date,
 JSON_EXTRACT(e.item,'$.endDate') end_date
 FROM resumes r JOIN JSON_TABLE(r.content_json,'$.experience[*]' COLUMNS(idx FOR ORDINALITY,item JSON PATH '$')) e WHERE r.id=1;
SELECT a.id,r.job_id,JSON_EXTRACT(r.item,'$.matchPoints') matched,
 JSON_EXTRACT(r.item,'$.missingPoints') missing
 FROM ai_analysis_request a JOIN JSON_TABLE(a.result_json,'$.recommendations[*]' COLUMNS(job_id BIGINT PATH '$.jobId',item JSON PATH '$')) r
 WHERE a.resume_id=1 AND r.job_id=3599 ORDER BY a.id DESC LIMIT 1;
SELECT id,updated_at,
 JSON_LENGTH(JSON_EXTRACT(content_json,'$.projects')) project_count,
 CHAR_LENGTH(JSON_EXTRACT(content_json,'$.projects')) project_chars,
 JSON_LENGTH(JSON_EXTRACT(content_json,'$.experience')) experience_count,
 CHAR_LENGTH(JSON_EXTRACT(content_json,'$.experience')) experience_chars,
 CHAR_LENGTH(JSON_EXTRACT(content_json,'$.techStack')) skill_chars,
 CHAR_LENGTH(JSON_EXTRACT(content_json,'$.introduction')) introduction_chars
 FROM resumes WHERE id=1;
SELECT COUNT(*) attachment_count FROM resume_attachments WHERE resume_id=1;
SELECT id,created_at,
 JSON_EXTRACT(result_json,'$.aiReview.status') review_status,
 JSON_EXTRACT(result_json,'$.aiReview.fitVersion') fit_version,
 JSON_LENGTH(JSON_EXTRACT(result_json,'$.aiReview.findings')) findings_count,
 JSON_LENGTH(JSON_EXTRACT(result_json,'$.aiReview.strengths')) strengths_count,
 JSON_EXTRACT(result_json,'$.aiReview.discardedFindings') discarded_findings,
 JSON_LENGTH(JSON_EXTRACT(result_json,'$.aiReview.jobs')) reviewed_jobs,
 JSON_LENGTH(JSON_EXTRACT(result_json,'$.detectedSkills')) detected_skills
 FROM ai_analysis_request WHERE resume_id=1 ORDER BY id DESC LIMIT 5;
SELECT a.id analysis_id,j.id job_id,j.title,j.experience,j.location,
 CHAR_LENGTH(j.apply_qual) qualification_chars,
 JSON_UNQUOTE(JSON_EXTRACT(r.item,'$.decision')) decision,
 JSON_UNQUOTE(JSON_EXTRACT(r.item,'$.decisionReason')) reason,
 JSON_LENGTH(JSON_EXTRACT(r.item,'$.criteria')) criteria_count
 FROM ai_analysis_request a
 JOIN JSON_TABLE(a.result_json,'$.aiReview.jobs[*]' COLUMNS(job_id BIGINT PATH '$.jobId',item JSON PATH '$')) r
 JOIN job_posts j ON j.id=r.job_id
 WHERE a.resume_id=1 ORDER BY a.id DESC,j.id LIMIT 25;
SELECT id,title,experience,location,LEFT(apply_qual,5000) qualifications
 FROM job_posts WHERE title='Server Developer [Commerce]' ORDER BY id DESC LIMIT 3;
SELECT 'account_count' metric,COUNT(*) value FROM users WHERE email={identity};
SELECT r.id resume_id,r.updated_at,JSON_EXTRACT(r.content_json,'$.careerProfile.location') locations,
 (u.email={identity}) matches_requested_email
 FROM resumes r JOIN users u ON u.id=r.user_id WHERE r.id=1;
SELECT r.id resume_id,r.updated_at,JSON_EXTRACT(r.content_json,'$.careerProfile.location') locations,
 JSON_EXTRACT(r.content_json,'$.careerProfile.category') categories
 FROM resumes r JOIN users u ON u.id=r.user_id WHERE u.email={identity} ORDER BY r.updated_at DESC,r.id DESC LIMIT 20;
SELECT a.id analysis_id,a.resume_id,a.created_at,a.recommendation_algo_version,
 JSON_EXTRACT(a.result_json,'$.derivedPreferences.location') analysis_locations,
 JSON_EXTRACT(a.result_json,'$.aiReview.fitVersion') fit_version
 FROM ai_analysis_request a JOIN users u ON u.id=a.user_id WHERE u.email={identity} ORDER BY a.id DESC LIMIT 10;
SELECT DISTINCT j.id,j.title,j.location FROM ai_analysis_request a JOIN users u ON u.id=a.user_id
 JOIN JSON_TABLE(a.result_json,'$.recommendations[*]' COLUMNS(job_id BIGINT PATH '$.jobId')) rec
 JOIN job_posts j ON j.id=rec.job_id WHERE u.email={identity} ORDER BY j.id DESC LIMIT 20;
ROLLBACK;
"""
args = ['--batch', '--raw', '--default-character-set=utf8mb4', '-h', url.hostname, '-P', str(url.port or 3306),
        '-u', env['SPRING_DATASOURCE_USERNAME'], url.path.lstrip('/')]
process_env = dict(os.environ, MYSQL_PWD=env['SPRING_DATASOURCE_PASSWORD'])
client = shutil.which('mysql')
command = [client, *args] if client else ['docker', 'run', '--rm', '-i', '--network', 'host', '-e', 'MYSQL_PWD',
                                      'mysql:8.4', 'mysql', *args]
result = subprocess.run(command, input=query, text=True, capture_output=True, env=process_env)
if result.returncode:
    diagnostic = result.stderr
    for value in (env['SPRING_DATASOURCE_PASSWORD'], env['SPRING_DATASOURCE_USERNAME'], url.hostname, email):
        if value:
            diagnostic = diagnostic.replace(value, '[redacted]')
    print(diagnostic[:2000], flush=True)
    raise SystemExit('Read-only database audit failed; credential-bearing diagnostics suppressed')
print(result.stdout)

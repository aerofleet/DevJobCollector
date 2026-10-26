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
identity = "CONVERT(0x" + email.encode('utf-8').hex() + " USING utf8mb4)"
query = f"""
START TRANSACTION READ ONLY;
SELECT 'account_count' metric,COUNT(*) value FROM users WHERE email={identity};
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

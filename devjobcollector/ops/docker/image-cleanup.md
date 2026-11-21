# DJC 이미지 정리

서버 원본 `/opt/itsdev/scripts/docker-image-cleanup.sh`를 확인한 뒤 저장소 관리 버전을 작성했다. 기본 실행은 dry-run이다. `--apply`는 `--protect IMAGE`를 하나 이상 요구하며, 배포 스크립트는 교체 전 현재 및 레거시 컨테이너의 이미지 ID를 전달한다. 이전 이미지가 확인되지 않으면 자동 정리를 건너뛴다.

대상은 DJC 저장소의 태그뿐이다. 고유 이미지 최신 10개, 모든 컨테이너의 이미지, 전달된 롤백 이미지, `latest`가 가리키는 이미지를 보존한다. 다른 저장소 태그, dangling 이미지, 볼륨 및 네트워크는 정리하지 않는다. 정리와 배포는 기존 서버 스크립트와 같은 `/run/lock/djc-image-cleanup.lock`을 사용한다. 이 잠금을 사용하지 않는 수동 Docker 조작은 별도 통제가 필요하다.

배포 후 health가 UP일 때만 정리하며, 정리 실패는 경고로 남기고 정상 배포를 롤백하지 않는다. 자동 정리로 디스크 사용량이 얼마나 감소하는지는 아직 측정하지 않았다.

검증: `bash -n ops/docker/deploy.sh ops/docker/docker-image-cleanup.sh` 및 `python3 -m unittest discover -s ops/docker -p 'test_*.py' -v`. 실제 운영 적용 전 현재 이미지와 롤백 태그, dry-run 후보, 배포 후 health와 이전 이미지 보존을 확인한다. 적용 여부는 별도 운영 승인을 따른다.

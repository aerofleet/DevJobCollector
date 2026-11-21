#!/usr/bin/env bash
set -euo pipefail

# Run under the same lock as deploy.sh; never prune Docker globally.
repo='ghcr.io/aerofleet/devjobcollector'
mode=--dry-run
protected_refs=()
while (($#)); do
  case "$1" in
    --dry-run|--apply) mode="$1"; shift ;;
    --protect) (($# >= 2)) || { echo 'Missing --protect value' >&2; exit 2; }; protected_refs+=("$2"); shift 2 ;;
    *) echo 'Usage: docker-image-cleanup.sh [--dry-run|--apply] [--protect IMAGE]...' >&2; exit 2 ;;
  esac
done
if [[ "$mode" == --apply && ${#protected_refs[@]} -eq 0 ]]; then
  echo 'Refusing apply without an explicit rollback image' >&2
  exit 2
fi

# deploy.sh passes the already locked descriptor; standalone runs take the lock.
if [[ "${DJC_DEPLOY_LOCK_HELD:-}" != 1 ]]; then
  exec 9>/run/lock/djc-image-cleanup.lock
fi
flock -n 9 || { echo 'Deployment or cleanup is already running' >&2; exit 1; }

declare -A protected=() seen=()
for ref in "${protected_refs[@]}"; do
  id=$(docker image inspect --format '{{.Id}}' "$ref") || exit 1
  [[ "$id" == sha256:* ]] || { echo 'Invalid protected image ID' >&2; exit 1; }
  protected["$id"]=1
done
containers=$(docker ps -aq) || exit 1
for container in $containers; do
  id=$(docker inspect --format '{{.Image}}' "$container") || exit 1
  protected["$id"]=1
done

# Docker can list the same ID for multiple tags. Sort by immutable creation
# timestamp and deduplicate before counting the ten images to retain.
listed=$(docker image ls "$repo" --no-trunc --format '{{.ID}}') || exit 1
inventory=''
for id in $listed; do
  [[ -n "${seen[$id]:-}" ]] && continue
  seen["$id"]=1
  metadata=$(docker image inspect --format '{{.Created}} {{.Id}}' "$id") || exit 1
  read -r created inspected extra <<< "$metadata"
  [[ "$created" == ????-??-??T* && "$inspected" == "$id" && -z "${extra:-}" ]] || {
    echo 'Invalid image inventory metadata' >&2; exit 1;
  }
  inventory+="$metadata"$'\n'
done
mapfile -t images < <(printf '%s' "$inventory" | LC_ALL=C sort -r | awk 'NF == 2 {print $2}')

index=0 candidates=0 failed=0
for id in "${images[@]}"; do
  ((index+=1))
  if ((index <= 10)) || [[ -n "${protected[$id]:-}" ]]; then
    echo "[KEEP] $id"
    continue
  fi
  # Recheck container references immediately before touching tags.
  users=$(docker ps -aq --filter "ancestor=$id") || exit 1
  if [[ -n "$users" ]]; then echo "[KEEP] $id (container)"; continue; fi
  tags=$(docker image inspect --format '{{range .RepoTags}}{{println .}}{{end}}' "$id") || exit 1
  if printf '%s\n' "$tags" | grep -Fxq "$repo:latest"; then
    echo "[KEEP] $id (latest)"
    continue
  fi
  while IFS= read -r tag; do
    [[ "$tag" == "$repo:"* ]] || continue
    ((candidates+=1))
    if [[ "$mode" == --dry-run ]]; then
      echo "[DRY-RUN] $tag"
    else
      # Do not remove a tag that moved since inventory was built.
      tag_id=$(docker image inspect --format '{{.Id}}' "$tag") || { ((failed+=1)); continue; }
      if [[ "$tag_id" != "$id" ]]; then
        echo "[FAILED] tag changed during cleanup: $tag" >&2
        ((failed+=1))
      elif ! docker image rm "$tag"; then
        echo "[FAILED] $tag" >&2
        ((failed+=1))
      fi
    fi
  done <<< "$tags"
done
echo "Candidate tags: $candidates; failed removals: $failed"
((failed == 0))

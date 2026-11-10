#!/usr/bin/env bash
# 递增 Android 版本号并同步 gradle.properties。
set -euo pipefail

PART="${1:-patch}"
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd)"
VERSION_FILE="$ROOT/VERSION"
GRADLE_FILE="$ROOT/gradle.properties"

CURRENT="$(tr -d '[:space:]' < "$VERSION_FILE")"
if [[ ! "$CURRENT" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "VERSION 格式无效: $CURRENT" >&2
    exit 1
fi
MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"

case "$PART" in
    major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
    minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
    patch) PATCH=$((PATCH + 1)) ;;
    *) echo "用法: $0 [major|minor|patch]" >&2; exit 1 ;;
esac
NEXT="$MAJOR.$MINOR.$PATCH"

printf '%s\n' "$NEXT" > "$VERSION_FILE"
CODE="$(sed -n 's/^APP_VERSION_CODE=\([0-9][0-9]*\)$/\1/p' "$GRADLE_FILE")"
if [[ -z "$CODE" ]]; then
    echo "gradle.properties 缺少 APP_VERSION_CODE" >&2
    exit 1
fi
CODE=$((CODE + 1))
sed -i -E "s/^APP_VERSION=.*/APP_VERSION=$NEXT/; s/^APP_VERSION_CODE=.*/APP_VERSION_CODE=$CODE/" "$GRADLE_FILE"

echo "Version: $CURRENT -> $NEXT"
echo "APP_VERSION_CODE: $CODE"
echo "Next: update CHANGELOG.md, build, commit, tag v$NEXT"

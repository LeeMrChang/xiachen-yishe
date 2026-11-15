#!/usr/bin/env bash
set -euo pipefail
: "${WEB_ROOT:?}" "${TAG:?}" "${ARCHIVE:?}"
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || exit 1
mkdir -p "$WEB_ROOT/releases/$TAG"
exec 9>"$WEB_ROOT/.deploy.lock"
flock -w 120 9
tar -xzf "$ARCHIVE" -C "$WEB_ROOT/releases/$TAG"
test -s "$WEB_ROOT/releases/$TAG/index.html"
ln -s "releases/$TAG" "$WEB_ROOT/.current-$TAG"
mv -Tf "$WEB_ROOT/.current-$TAG" "$WEB_ROOT/current"
echo "Admin web deployed $TAG"

#!/usr/bin/env bash

set -euo pipefail

dependency_report="$(mktemp)"
trap 'rm -f "$dependency_report"' EXIT

./gradlew -q :runtime:dependencies --configuration debugRuntimeClasspath > "$dependency_report"

if grep -Eq 'androidx\.compose|project :utils|org\.koin:koin-core' "$dependency_report"; then
    echo "The core runtime must not expose Compose, app utilities, or standard Koin." >&2
    grep -E 'androidx\.compose|project :utils|org\.koin:koin-core' "$dependency_report" >&2
    exit 1
fi

if ! grep -q 'io\.insert-koin:embedded-koin-core' "$dependency_report"; then
    echo "The core runtime must use the SDK-relocated Koin dependency." >&2
    exit 1
fi

echo "Core runtime dependency isolation verified."

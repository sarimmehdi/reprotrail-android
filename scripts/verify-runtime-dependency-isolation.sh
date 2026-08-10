#!/usr/bin/env bash

set -euo pipefail

dependency_report="$(mktemp)"
trap 'rm -f "$dependency_report"' EXIT

./gradlew -q :runtime:dependencies --configuration debugRuntimeClasspath > "$dependency_report"

if grep -Eq 'androidx\.compose|project :utils' "$dependency_report"; then
    echo "The core runtime must not depend on Compose or the app :utils module." >&2
    grep -E 'androidx\.compose|project :utils' "$dependency_report" >&2
    exit 1
fi

echo "Core runtime dependency isolation verified."

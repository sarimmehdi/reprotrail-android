#!/usr/bin/env bash

set -euo pipefail

limitations_file="KNOWN_LIMITATIONS.md"

if [[ ! -s "$limitations_file" ]]; then
    echo "A non-empty KNOWN_LIMITATIONS.md is required for release qualification." >&2
    exit 1
fi

required_columns=(
    "Component and versions"
    "User impact"
    "Trigger"
    "Detection"
    "Mitigation"
    "Security or privacy consequence"
    "Removal target"
)

for column in "${required_columns[@]}"; do
    if ! grep -Fq "$column" "$limitations_file"; then
        echo "KNOWN_LIMITATIONS.md is missing the '$column' field." >&2
        exit 1
    fi
done

if ! grep -Fq "[Known limitations](KNOWN_LIMITATIONS.md)" README.md; then
    echo "README.md must link to KNOWN_LIMITATIONS.md." >&2
    exit 1
fi

echo "Release limitation metadata verified."

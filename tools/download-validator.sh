#!/usr/bin/env bash
# Downloads the HL7 FHIR validator CLI and pre-installs gematik/KBV FHIR packages
# that are only available on Simplifier.net (not on packages.fhir.org).
#
# The validator checks ~/.fhir/packages/ before hitting any online registry,
# so packages installed here are used automatically.
#
# Usage:
#   bash tools/download-validator.sh           # download default version
#   VALIDATOR_VERSION=6.3.12 bash tools/download-validator.sh
set -euo pipefail

VALIDATOR_VERSION="${VALIDATOR_VERSION:-6.3.12}"
JAR="$(dirname "$0")/validator_cli.jar"

if [ -f "$JAR" ]; then
    echo "validator_cli.jar already present at $JAR (delete it to re-download)"
else
    echo "Downloading FHIR validator v${VALIDATOR_VERSION} ..."
    curl -fL -o "$JAR" \
        "https://github.com/hapifhir/org.hl7.fhir.core/releases/download/${VALIDATOR_VERSION}/validator_cli.jar"
    echo "Saved to $JAR"
fi

# Install a FHIR package from Simplifier.net into ~/.fhir/packages/<name>#<version>/
# The validator's local cache uses exactly this layout.
install_from_simplifier() {
    local name="$1"
    local version="$2"
    local target="${HOME}/.fhir/packages/${name}#${version}"
    if [ -d "$target" ]; then
        echo "  $name#$version already in cache, skipping"
        return
    fi
    local url="https://packages.simplifier.net/${name}/-/${name}-${version}.tgz"
    echo "  Installing $name#$version from Simplifier ..."
    mkdir -p "$target"
    curl -fL "$url" | tar -xz -C "$target"
    echo "  Done: $target"
}

echo ""
echo "Pre-installing packages from Simplifier.net (not available on packages.fhir.org) ..."
install_from_simplifier "de.gematik.epa.medication"   "1.3.0"
install_from_simplifier "de.gematik.epa"              "1.2.0"
install_from_simplifier "de.gematik.terminology"      "1.0.7"

echo ""
echo "Run validation with:"
echo "  mvn validate -Pfhir-validate"

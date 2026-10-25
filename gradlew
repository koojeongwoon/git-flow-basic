#!/bin/sh
set -eu
exec "${SPEC_LINEAGE_GRADLE_HOME:-/runner/gradle}/bin/gradle" "$@"

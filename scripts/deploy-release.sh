#!/usr/bin/env bash
set -euo pipefail
readonly deploy_variant='release'
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/deploy-common.sh"

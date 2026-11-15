#!/bin/bash
set -e

echo "=== Entrypoint: Starting application ==="

# Execute the main command as appuser
exec gosu appuser "$@"
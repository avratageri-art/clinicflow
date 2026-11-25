#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/clinicflow"

echo "==============================================================================="
echo "   CLINICFLOW: ML-Based Patient No-Show Prediction and Smart Queue System"
echo "==============================================================================="

if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java 17 was not found in PATH. Please install Java 17 LTS." >&2
    exit 1
fi

JAR_PATH="target/clinicflow-1.0.0-SNAPSHOT.jar"
if [ -f "$JAR_PATH" ]; then
    echo "[INFO] Launching packaged JAR: $JAR_PATH"
    exec java -jar "$JAR_PATH" "$@"
else
    if ! command -v mvn >/dev/null 2>&1; then
        echo "[ERROR] Maven (mvn) not found and packaged JAR is absent." >&2
        exit 1
    fi
    echo "[INFO] Packaged JAR not found. Launching via Maven exec:java..."
    exec mvn exec:java -Dexec.args="$*"
fi

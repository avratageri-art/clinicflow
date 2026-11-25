# Contributing to ClinicFlow

Thank you for your interest in contributing to **ClinicFlow**!

## Development Setup

1. **Prerequisites**:
   - Java 17 LTS (JDK 17)
   - Apache Maven 3.8+
2. **Clone and Build**:
   ```bash
   git clone <repository-url>
   cd JAVA_ML/clinicflow
   mvn clean test
   ```
3. **Train / Regenerate the ML Model**:
   The serialized Weka Random Forest model (`models/no-show-random-forest.model`, ~234 MB) is excluded from Git due to GitHub's 100 MB file size limit. Generate it locally on first run:
   ```bash
   mvn exec:java -Dexec.args="--cli --retrain"
   ```
   Or launch the Swing GUI (`mvn exec:java`) and click **Train / Retrain Model** in the **ML Model Insights** panel.

## Coding Standards

- Target **Java 17** language features.
- Follow standard Java naming conventions (`PascalCase` for classes, `camelCase` for methods/variables, `UPPER_SNAKE_CASE` for constants).
- Keep UI logic (`com.clinicflow.ui`) decoupled from business logic (`com.clinicflow.service`), persistence (`com.clinicflow.repository`), and ML (`com.clinicflow.ml`).
- Never commit `.env` files, credentials, generated `target/` artifacts, or `.model` binaries.

## Pull Request Process

1. Fork the repository and create a feature branch (`git checkout -b feat/your-feature`).
2. Add or update JUnit 5 unit tests in `clinicflow/src/test/java/com/clinicflow/`.
3. Verify the full test suite passes (`mvn clean test`).
4. Update relevant documentation in `README.md` or `clinicflow/docs/`.
5. Open a Pull Request with a clear summary of your changes and test results.

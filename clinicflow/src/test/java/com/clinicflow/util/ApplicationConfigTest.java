package com.clinicflow.util;

import com.clinicflow.service.ModelInsightsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApplicationConfig environment/root resolution and ModelInsightsService report parsing.
 */
class ApplicationConfigTest {

    @Test
    void detectsNestedClinicFlowDirectoryWhenLaunchedFromRepoRoot(@TempDir Path tempRoot) throws IOException {
        Path nested = tempRoot.resolve("clinicflow");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);

        ApplicationConfig config = new ApplicationConfig(tempRoot, Map.of());

        assertThat(config.getProjectRoot()).isEqualTo(nested);
        assertThat(config.getModelsPath()).isEqualTo(nested.resolve("models"));
    }

    @Test
    void honorsValidEnvOverridesAndFallsBackOnInvalidValues(@TempDir Path tempRoot) {
        ApplicationConfig valid = new ApplicationConfig(tempRoot, Map.of(
                "CLINICFLOW_HOME", tempRoot.toString(),
                "CLINICFLOW_TRAIN_TEST_SPLIT", "0.75",
                "CLINICFLOW_LOW_RISK_THRESHOLD", "0.25",
                "CLINICFLOW_HIGH_RISK_THRESHOLD", "0.60"
        ));

        assertThat(valid.getProjectRoot()).isEqualTo(tempRoot);
        assertThat(valid.getTrainTestSplitRatio()).isEqualTo(0.75);
        assertThat(valid.getLowRiskThreshold()).isEqualTo(0.25);
        assertThat(valid.getHighRiskThreshold()).isEqualTo(0.60);

        ApplicationConfig invalid = new ApplicationConfig(tempRoot, Map.of(
                "CLINICFLOW_TRAIN_TEST_SPLIT", "not-a-number",
                "CLINICFLOW_LOW_RISK_THRESHOLD", "0.80",
                "CLINICFLOW_HIGH_RISK_THRESHOLD", "0.20"
        ));

        assertThat(invalid.getTrainTestSplitRatio()).isEqualTo(0.80);
        assertThat(invalid.getLowRiskThreshold()).isEqualTo(0.20);
        assertThat(invalid.getHighRiskThreshold()).isEqualTo(0.50);
    }

    @Test
    void modelInsightsServiceParsesConfusionMatrixCsv(@TempDir Path tempRoot) throws IOException {
        Path reportsDir = tempRoot.resolve("reports");
        Files.createDirectories(reportsDir);
        Files.createDirectories(tempRoot.resolve("models"));
        Files.writeString(
                reportsDir.resolve("confusion-matrix.csv"),
                "Actual\\Predicted,Predicted_NO(Attended),Predicted_YES(NoShow),Total_Actual\n"
                        + "Actual_NO(Attended),100,20,120\n"
                        + "Actual_YES(NoShow),15,35,50\n"
                        + "Total_Predicted,115,55,170\n",
                StandardCharsets.UTF_8
        );

        ApplicationConfig config = new ApplicationConfig(tempRoot, Map.of("CLINICFLOW_HOME", tempRoot.toString()));
        ModelInsightsService service = new ModelInsightsService(config);
        ModelInsightsService.ModelSpec spec = service.getModelSpecification();

        assertThat(spec.trueNegatives).isEqualTo(100L);
        assertThat(spec.falsePositives).isEqualTo(20L);
        assertThat(spec.falseNegatives).isEqualTo(15L);
        assertThat(spec.truePositives).isEqualTo(35L);
    }
}

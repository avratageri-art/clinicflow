package com.clinicflow.ml;

import com.clinicflow.util.ApplicationConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetInspectionTest {

    @Test
    @DisplayName("Inspect compact self-contained appointments dataset and optional full Kaggle CSV")
    void testInspectDataset() {
        ApplicationConfig config = ApplicationConfig.getInstance();
        File effectiveData = config.getEffectiveRawDataPath().toFile();

        assertThat(effectiveData).exists().isFile();

        CsvDatasetLoader loader = new CsvDatasetLoader();
        List<String> headers = loader.getHeaders(effectiveData);
        assertThat(headers).containsExactly(
                "PatientId", "AppointmentID", "Gender", "ScheduledDay", "AppointmentDay",
                "Age", "Neighbourhood", "Scholarship", "Hipertension", "Diabetes",
                "Alcoholism", "Handcap", "SMS_received", "No-show"
        );

        DatasetInspector inspector = new DatasetInspector(loader);
        DatasetProfile profile = inspector.inspect(effectiveData);

        assertThat(profile.getTotalRecords()).isGreaterThanOrEqualTo(200);
        assertThat(profile.getTotalColumns()).isEqualTo(14);
        assertThat(profile.getShowedUpCount() + profile.getNoShowCount()).isEqualTo(profile.getTotalRecords());

        for (String col : headers) {
            assertThat(profile.getMissingValuesPerColumn().get(col))
                    .as("Column " + col + " should have 0 missing values")
                    .isEqualTo(0);
        }

        String markdown = profile.toMarkdown();
        assertThat(markdown).contains("ClinicFlow — Dataset Inspection & Quality Profile");
        assertThat(markdown).contains("Data Leakage Prevention");

        // Additional exact assertions when the full 110,527-row Kaggle dataset is present locally
        File fullData = config.getRawDataPath().toFile();
        if (Files.isRegularFile(fullData.toPath()) && fullData.length() > 10_000_000L) {
            assertThat(profile.getTotalRecords()).isEqualTo(110527);
            assertThat(profile.getShowedUpCount()).isEqualTo(88208);
            assertThat(profile.getNoShowCount()).isEqualTo(22319);
        }
    }
}

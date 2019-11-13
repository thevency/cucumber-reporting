package net.masterthought.cucumber;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;

import net.masterthought.cucumber.generators.ErrorPage;
import net.masterthought.cucumber.generators.FeatureReportPage;
import net.masterthought.cucumber.generators.FeaturesOverviewPage;
import net.masterthought.cucumber.generators.TagReportPage;
import net.masterthought.cucumber.generators.TagsOverviewPage;
import net.masterthought.cucumber.json.Feature;
import net.masterthought.cucumber.json.support.TagObject;

public class ReportBuilder {

    private static final Logger LOG = Logger.getLogger(ReportBuilder.class.getName());

    /**
     * Page that should be displayed when the reports is generated. Shared between {@link FeaturesOverviewPage} and
     * {@link ErrorPage}.
     */
    public static final String HOME_PAGE = "overview-features.html";

    /**
     * Subdirectory where the report will be created.
     */
    public static final String BASE_DIRECTORY = "cucumber-html-reports";


    private ReportResult reportResult;
    private final ReportParser reportParser;

    private Configuration configuration;
    private List<String> jsonFiles;

    /**
     * Flag used to detect if the file with updated trends is saved.
     * If the report crashes and the trends was not saved then it tries to save trends again with empty data
     * to mark that the build crashed.
     */

    public ReportBuilder(List<String> jsonFiles, Configuration configuration) {
        this.jsonFiles = jsonFiles;
        this.configuration = configuration;
        reportParser = new ReportParser(configuration);
    }

    /**
     * Parses provided files and generates the report. When generating process fails
     * report with information about error is provided.
     * @return stats for the generated report
     */
    public Reportable generateReports() {

        try {
            // first copy static resources so ErrorPage is displayed properly
            copyStaticResources();

            // create directory for embeddings before files are generated
            createEmbeddingsDirectory();

            // add metadata info sourced from files
            reportParser.parseClassificationsFiles(configuration.getClassificationFiles());

            // parse json files for results
            List<Feature> features = reportParser.parseJsonFiles(jsonFiles);
            reportResult = new ReportResult(features, configuration);
            Reportable reportable = reportResult.getFeatureReport();

            // Collect and generate pages in a single pass
            generatePages();
            return reportable;

            // whatever happens we want to provide at least error page instead of incomplete report or exception
        } catch (Exception e) {
            generateErrorPage(e);
            // something went wrong, don't pass result that might be incomplete
            return null;
        }
    }

    private void copyStaticResources() {
        copyResources("css", "cucumber.css", "bootstrap.min.css", "font-awesome.min.css");
        copyResources("js", "jquery.min.js", "jquery.tablesorter.min.js", "bootstrap.min.js", "Chart.min.js",
                "moment.min.js");
        copyResources("fonts", "FontAwesome.otf", "fontawesome-webfont.svg", "fontawesome-webfont.woff",
                "fontawesome-webfont.eot", "fontawesome-webfont.ttf", "fontawesome-webfont.woff2",
                "glyphicons-halflings-regular.eot", "glyphicons-halflings-regular.eot",
                "glyphicons-halflings-regular.woff2", "glyphicons-halflings-regular.woff",
                "glyphicons-halflings-regular.ttf", "glyphicons-halflings-regular.svg");
        copyResources("images", "favicon.png");
    }

    private void createEmbeddingsDirectory() {
        configuration.getEmbeddingDirectory().mkdirs();
    }

    private void copyResources(String resourceLocation, String... resources) {
        for (String resource : resources) {
            File tempFile = new File(configuration.getReportDirectory().getAbsoluteFile(),
                    BASE_DIRECTORY + File.separatorChar + resourceLocation + File.separatorChar + resource);
            // don't change this implementation unless you verified it works on Jenkins
            try {
                FileUtils.copyInputStreamToFile(
                        this.getClass().getResourceAsStream("/" + resourceLocation + "/" + resource), tempFile);
            } catch (IOException e) {
                // based on FileUtils implementation, should never happen even is declared
                throw new ValidationException(e);
            }
        }
    }

	private void generatePages() {
		new FeaturesOverviewPage(reportResult, configuration).generatePage();

		for (Feature feature : reportResult.getAllFeatures()) {
			new FeatureReportPage(reportResult, configuration, feature).generatePage();
		}

		new TagsOverviewPage(reportResult, configuration).generatePage();

		for (TagObject tagObject : reportResult.getAllTags()) {
			new TagReportPage(reportResult, configuration, tagObject).generatePage();
		}


	}

    private void generateErrorPage(Exception exception) {
        LOG.log(Level.INFO, "Unexpected error", exception);
        ErrorPage errorPage = new ErrorPage(reportResult, configuration, exception, jsonFiles);
        errorPage.generatePage();
    }
}

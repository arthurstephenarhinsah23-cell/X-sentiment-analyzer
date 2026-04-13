package org.example.tutorial;

// Stanford CoreNLP imports for natural language processing
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;

// JavaFX imports for building the graphical user interface
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;

// Java I/O imports for file handling
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Main class for a sentiment analysis tool targeting X (Twitter) tweets.
 * It provides a GUI to upload a CSV file or type a tweet, analyze sentiment,
 * display results with a pie chart, and export results to a text file.
 */
public class XSentimentAnalysisFX extends Application {

    // UI components that need to be accessible across methods
    private TextArea resultArea;          // Displays detailed sentiment results
    private Label summaryLabel;           // Shows summary statistics
    private ProgressBar progressBar;      // Indicates analysis progress
    private File selectedCSV;             // The CSV file selected by the user
    private PieChart pieChart;            // Pie chart for sentiment distribution
    private Button exportButton;          // Button to export results

    /**
     * Entry point of the JavaFX application. Launches the GUI.
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        launch();
    }

    /**
     * Initializes the JavaFX stage and sets up all UI components,
     * event handlers, and layout.
     * @param stage the primary stage for this application
     */
    @Override
    public void start(Stage stage) {

        // ---------------- TITLE ----------------
        Label title = new Label(" Sentiment Analyzer");
        title.getStyleClass().add("title-label");

        // ---------------- RESULT AREA ----------------
        // A non-editable text area to display each tweet and its sentiment
        resultArea = new TextArea();
        resultArea.setPrefHeight(400);
        resultArea.setPrefWidth(500);
        resultArea.setEditable(false);

        // ---------------- SUMMARY LABEL ----------------
        // A label that shows the final counts of positive, negative, and neutral tweets
        summaryLabel = new Label();
        summaryLabel.setPrefWidth(200);
        summaryLabel.setPrefHeight(180);
        summaryLabel.setWrapText(true);
        summaryLabel.getStyleClass().add("summary-box");

        // ---------------- PIE CHART ----------------
        // Pie chart to visualize sentiment distribution
        pieChart = new PieChart();
        pieChart.setTitle("Sentiment Distribution");
        pieChart.setPrefSize(250, 250);
        pieChart.setLabelsVisible(true);
        pieChart.setLegendVisible(true);

        // Combine summary and chart into a vertical box (right panel)
        VBox rightPanel = new VBox(10, summaryLabel, pieChart);
        rightPanel.setAlignment(Pos.TOP_CENTER);

        // ---------------- PROGRESS BAR ----------------
        // Shows progress while analyzing multiple tweets
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(760);

        // ---------------- MANUAL TWEET INPUT ----------------
        Label manualLabel = new Label("Or Type a Tweet:");
        TextField tweetInput = new TextField();
        tweetInput.setPromptText("Type your tweet here...");
        tweetInput.setPrefWidth(600);

        // ---------------- BUTTONS ----------------
        Button uploadButton = new Button("Upload CSV (Tweet)");
        Button analyzeButton = new Button("Analyze Tweets");
        exportButton = new Button("Export Results");
        exportButton.setDisable(true);  // Disabled until analysis is done

        uploadButton.getStyleClass().add("main-button");
        analyzeButton.getStyleClass().add("main-button");
        exportButton.getStyleClass().add("main-button");

        // ---------------- UPLOAD CSV ----------------
        // Handles file selection and clears previous data
        uploadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select CSV File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("CSV Files", "*.csv")
            );

            File file = fileChooser.showOpenDialog(stage);

            if (file != null) {
                selectedCSV = file;

                // Clear everything
                tweetInput.clear();
                resultArea.clear();
                summaryLabel.setText("");
                progressBar.setProgress(0);
                // Reset chart
                pieChart.setData(FXCollections.observableArrayList());
                exportButton.setDisable(true);   // Disable export button after new upload

                resultArea.appendText("CSV uploaded: " + file.getName() + "\n");
            }
        });

        // ---------------- ANALYZE ----------------
        // Triggered when the user clicks "Analyze Tweets"
        analyzeButton.setOnAction(e -> {

            String typedTweet = tweetInput.getText();

            // Check if there is any input (either typed tweet or uploaded CSV)
            if ((typedTweet == null || typedTweet.trim().isEmpty()) && selectedCSV == null) {
                resultArea.appendText("Please upload CSV or type a tweet first.\n");
                return;
            }

            // Reset UI for new analysis
            resultArea.clear();
            summaryLabel.setText("");
            progressBar.setProgress(0);
            pieChart.setData(FXCollections.observableArrayList());
            exportButton.setDisable(true);   // Disable export while analyzing

            // Perform analysis in a separate thread to keep UI responsive
            new Thread(() -> {
                try {
                    // Set up Stanford CoreNLP pipeline with required annotators
                    Properties props = new Properties();
                    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,parse,sentiment");
                    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

                    List<String> tweets = new ArrayList<>();

                    // PRIORITY: Typed tweet overrides CSV
                    if (typedTweet != null && !typedTweet.trim().isEmpty()) {
                        selectedCSV = null;           // Discard CSV if typed tweet is provided
                        tweets.add(typedTweet);
                        Platform.runLater(() ->
                                resultArea.appendText("Analyzing typed tweet...\n")
                        );
                    } else if (selectedCSV != null) {
                        Platform.runLater(() ->
                                resultArea.appendText("Analyzing CSV tweets...\n")
                        );

                        // Read up to 10 lines from the CSV file (limit to keep performance)
                        try (BufferedReader br = new BufferedReader(new FileReader(selectedCSV))) {
                            String line;
                            int limit = 10;
                            int csvCount = 0;
                            while ((line = br.readLine()) != null && csvCount < limit) {
                                tweets.add(line);
                                csvCount++;
                            }
                        }
                    }

                    int positive = 0;
                    int negative = 0;
                    int neutral = 0;
                    int count = 0;
                    int total = tweets.size();

                    // StringBuilder to accumulate detailed results
                    StringBuilder sb = new StringBuilder();
                    sb.append("\nTWEET TEXT | SENTIMENT\n");
                    sb.append("------------------------------------\n");

                    // Analyze each tweet
                    for (String tweet : tweets) {
                        String sentiment = analyzeSentiment(pipeline, tweet);
                        String displayTweet = cleanTweet(tweet);
                        sb.append(displayTweet).append(" | ").append(sentiment).append("\n");

                        // Update counters based on sentiment
                        if (sentiment.contains("Positive"))
                            positive++;
                        else if (sentiment.contains("Negative"))
                            negative++;
                        else
                            neutral++;

                        count++;
                        double progressValue = (double) count / total;
                        final double progress = progressValue;
                        Platform.runLater(() -> progressBar.setProgress(progress));
                    }

                    // Final values (make them effectively final for use inside Platform.runLater)
                    final int finalPositive = positive;
                    final int finalNegative = negative;
                    final int finalNeutral = neutral;
                    final int finalCount = count;
                    final String finalText = sb.toString();

                    // Update UI on JavaFX Application Thread
                    Platform.runLater(() -> {
                        resultArea.appendText(finalText);

                        summaryLabel.setText(
                                "---- SUMMARY ----\n\n" +
                                        "Processed: " + finalCount + "\n" +
                                        "Positive: " + finalPositive + "\n" +
                                        "Negative: " + finalNegative + "\n" +
                                        "Neutral: " + finalNeutral
                        );

                        // Update the PieChart with the new data
                        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList(
                                new PieChart.Data("Positive", finalPositive),
                                new PieChart.Data("Negative", finalNegative),
                                new PieChart.Data("Neutral", finalNeutral)
                        );
                        pieChart.setData(chartData);

                        // ----- PROGRAMMATIC COLORING (ensures colorful slices) -----
                        // Assign specific colors to each pie slice
                        Platform.runLater(() -> {
                            for (PieChart.Data data : pieChart.getData()) {
                                Node node = data.getNode();
                                if (node != null) {
                                    String color;
                                    switch (data.getName()) {
                                        case "Positive":
                                            color = "#4caf50"; // Green
                                            break;
                                        case "Negative":
                                            color = "#f44336"; // Red
                                            break;
                                        case "Neutral":
                                            color = "#ffc107"; // Amber
                                            break;
                                        default:
                                            color = "#cccccc";
                                    }
                                    node.setStyle("-fx-pie-color: " + color + ";");
                                }
                            }
                        });
                        // ---------------------------------------------------------

                        progressBar.setProgress(1.0);
                        exportButton.setDisable(false);   // Enable export after analysis
                    });

                } catch (Exception ex) {
                    // Handle any exceptions (e.g., NLP pipeline errors, file issues)
                    Platform.runLater(() ->
                            resultArea.setText("Error: " + ex.getMessage())
                    );
                    exportButton.setDisable(true);
                }
            }).start();
        });

        // ---------------- EXPORT RESULTS ----------------
        // Save the current results to a text file
        exportButton.setOnAction(e -> {
            // Get the current results from resultArea and summaryLabel
            String results = buildExportContent();
            if (results.isEmpty()) {
                resultArea.appendText("\nNo results to export.\n");
                return;
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Sentiment Results");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            fileChooser.setInitialFileName("sentiment_results.txt");
            File file = fileChooser.showSaveDialog(stage);

            if (file != null) {
                // Write to file in a background thread to avoid blocking UI
                new Thread(() -> {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                        writer.write(results);
                        Platform.runLater(() -> resultArea.appendText("\nResults exported to: " + file.getAbsolutePath() + "\n"));
                    } catch (IOException ex) {
                        Platform.runLater(() -> resultArea.appendText("\nError exporting results: " + ex.getMessage() + "\n"));
                    }
                }).start();
            }
        });

        // ---------------- LAYOUT ----------------
        // Arrange all components using HBox, VBox, and StackPane
        HBox resultSection = new HBox(15, resultArea, rightPanel);
        resultSection.setAlignment(Pos.CENTER);

        HBox buttonBox = new HBox(20, uploadButton, analyzeButton, exportButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox content = new VBox(10,
                title,
                manualLabel,
                tweetInput,
                resultSection,
                buttonBox,
                progressBar
        );

        content.setAlignment(Pos.TOP_CENTER);
        content.setStyle("-fx-padding: 15;");

        StackPane root = new StackPane(content);

        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/advance-style.css").toExternalForm());

        stage.setTitle("X Sentiment Analyzer");
        stage.setScene(scene);
        stage.show();

        // Attempt to set a custom icon; if not found, use default
        try {
            stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/icon.jpeg")));
        } catch (Exception e) {
            System.out.println("No icon.jpeg found in resources, using default Java icon.");
        }
    }

    /**
     * Analyzes the sentiment of a given text using the Stanford CoreNLP pipeline.
     * It processes the text and returns the sentiment class of the first sentence.
     *
     * @param pipeline the initialized StanfordCoreNLP object
     * @param text     the text to analyze
     * @return a string representing the sentiment (e.g., "Positive", "Negative", "Neutral")
     */
    private String analyzeSentiment(StanfordCoreNLP pipeline, String text) {
        Annotation annotation = new Annotation(text);
        pipeline.annotate(annotation);

        String sentiment = "Neutral";
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
        }
        return sentiment;
    }

    /**
     * Cleans a raw tweet string by removing a leading CSV column (if present) and
     * stripping surrounding double quotes. This helps to extract the actual tweet text
     * from a CSV line that may have an index or quote characters.
     *
     * @param raw the raw tweet text (may contain CSV structure)
     * @return the cleaned tweet text
     */
    private String cleanTweet(String raw) {
        if (raw == null) return "";
        int commaIndex = raw.indexOf(',');
        if (commaIndex > 0 && commaIndex < raw.length() - 1) {
            raw = raw.substring(commaIndex + 1).trim();
        }
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    /**
     * Builds the content to be exported to a file.
     * It combines the summary label text and the detailed results from the result area.
     *
     * @return a string containing the summary and detailed results
     */
    private String buildExportContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== X Sentiment Analysis Results ===\n\n");
        sb.append(summaryLabel.getText()).append("\n\n");
        sb.append("=== Detailed Results ===\n");
        sb.append(resultArea.getText());
        return sb.toString();
    }
}
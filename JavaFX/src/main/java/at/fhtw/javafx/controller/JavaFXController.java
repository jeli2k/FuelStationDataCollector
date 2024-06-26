package at.fhtw.javafx.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;

import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javafx.scene.control.TextFormatter;
import javafx.scene.control.ProgressIndicator;





public class JavaFXController {

    @FXML
    private TextField customerIdField;
    @FXML
    private Label statusLabel;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String API_URL = "http://localhost:8080/invoices/";

    @FXML
    private ProgressIndicator progressIndicator;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final int TIMEOUT_SECONDS = 9;
    private final int INITIAL_DELAY = 3000; // Initial delay in milliseconds (3 seconds)

    @FXML
    protected void generateInvoice() {
        String customerId = customerIdField.getText();
        if (!customerId.isEmpty()) {
            sendStartInvoiceRequest(customerId);
        } else {
            statusLabel.setText("Customer ID cannot be empty.");
        }
    }

    @FXML
    public void initialize() {
        TextFormatter<String> numericFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("\\d*")) {
                return change;
            }
            return null;
        });
        customerIdField.setTextFormatter(numericFormatter);
    }

    private void sendStartInvoiceRequest(String customerId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + customerId))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        Platform.runLater(() -> {
            progressIndicator.setVisible(true); // Show loading when request starts
            statusLabel.setText("Starting invoice generation for Customer ID: " + customerId);
        });

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        Platform.runLater(() -> {
                            statusLabel.setText("Invoice generation started for Customer ID: " + customerId);
                            startPeriodicCheck(customerId); // Continue to show loading icon
                        });
                    } else {
                        Platform.runLater(() -> {
                            statusLabel.setText("Failed to start invoice generation.");
                            progressIndicator.setVisible(false); // Hide loading if failed to start
                        });
                    }
                })
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        statusLabel.setText("Error: " + e.getMessage());
                        progressIndicator.setVisible(false); // Hide loading on error
                    });
                    return null;
                });
    }

    private void startPeriodicCheck(String customerId) {
        Task<Void> checkTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long startTime = System.currentTimeMillis();

                // Initial delay before first request
                Thread.sleep(INITIAL_DELAY);

                while (true) {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(API_URL + customerId))
                            .GET()
                            .build();

                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenAccept(response -> {
                                if (response.statusCode() == 200) {
                                    String responseBody = response.body();
                                    if (responseBody.startsWith("file://")) {
                                        Platform.runLater(() -> {
                                            statusLabel.setText("Invoice is ready for Customer ID: " + customerId);
                                            createDownloadButton(responseBody);
                                            progressIndicator.setVisible(false); // Hide loading when download is ready
                                        });
                                        cancel(); // Stop periodic checking
                                    }
                                }
                            })
                            .exceptionally(e -> {
                                e.printStackTrace();
                                return null;
                            });

                    // Check if the timeout period has been exceeded
                    if (System.currentTimeMillis() - startTime > TIMEOUT_SECONDS * 1000) {
                        Platform.runLater(() -> {
                            statusLabel.setText("Timeout: Failed to get invoice status.");
                            progressIndicator.setVisible(false);
                        });
                        cancel(); // Stop periodic checking
                        break;
                    }

                    Thread.sleep(3000); // Check every 3 seconds
                }
                return null;
            }
        };

        // Start the periodic check task
        new Thread(checkTask).start();
    }


    private Button downloadButton;

    private void createDownloadButton(String downloadLink) {
        // Remove existing download button if it exists
        if (downloadButton != null) {
            ((VBox) statusLabel.getParent()).getChildren().remove(downloadButton);
        }

        downloadButton = new Button("Download Invoice");
        downloadButton.setOnAction(e -> {
            try {
                URI uri = new URI(downloadLink);

                if (uri.getScheme().equals("file")) {
                    // Handle local file URL
                    File file = new File(uri);
                    if (file.exists()) {
                        Desktop.getDesktop().open(file);
                    } else {
                        throw new FileNotFoundException("File not found: " + file);
                    }
                } else {
                    // Handle web URL
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(uri);
                    } else {
                        throw new UnsupportedOperationException("Desktop browsing not supported.");
                    }
                }
            } catch (IOException | URISyntaxException | UnsupportedOperationException ex) {
                ex.printStackTrace();
            }
        });

        ((VBox) statusLabel.getParent()).getChildren().add(downloadButton);
    }
}
//Created by Alexey Yarygin
package freeHands.gui;

import freeHands.controller.BackController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

@Getter
public class MergeWindow {
    private Stage window;
    private TextArea logs;

    @SneakyThrows
    public MergeWindow() {
        window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        VBox mainVbox = new VBox(10);
        mainVbox.setPadding(new Insets(10, 10, 10, 10));
        HBox fieldsBox = new HBox(10);
        fieldsBox.setPadding(new Insets(10, 10, 10, 10));
        fieldsBox.setAlignment(Pos.CENTER_LEFT);

        TextField qty = new TextField();
        //Only digits can be written.
        qty.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                qty.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
        qty.setMaxWidth(50);

        TextField loc = new TextField();
        loc.setMaxWidth(100);

        ComboBox<String> project = new ComboBox<>();
        FileUtils.readLines(new File(Main.auth.getProperty("projectNames")), "UTF-8")
                .forEach(s -> project.getItems().add(s));
        project.setMaxWidth(100);

        TextField end = new TextField();
        end.setMaxWidth(100);

        fieldsBox.getChildren().addAll(
                new Label("QTY:"), qty,
                new Label("LOC:"), loc,
                new Label("Project"), project,
                new Label("Ending"), end
        );

        logs = new TextArea();
        logs.setEditable(false);
        Button mergeButton = new Button("Merge");
        mergeButton.setOnAction(e -> BackController.merge(mergeButton, logs, qty.getText(), loc.getText(),
                project.getSelectionModel().getSelectedItem(), end.getText()));
        mainVbox.getChildren().addAll(fieldsBox, logs, mergeButton);
        Scene scene = new Scene(mainVbox, 700, 300);
        scene.getStylesheets().add("/myStyle.css");
        window.setTitle("Merge");
        window.setOnCloseRequest(e -> {
            clearLogs();
        });
        window.setScene(scene);
    }

    private void clearLogs() {
        logs.clear();
    }

    public void display() {
        window.show();
    }

    public void hide() {
        window.hide();
    }
}

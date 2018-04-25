package freeHands.gui;

import freeHands.controller.BackController;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Getter;

@Getter
public class MergeWindow {
    private Stage window;
    private TextArea logs;

    public MergeWindow() {
        window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        VBox mainVbox = new VBox(10);
        mainVbox.setPadding(new Insets(10, 10, 10, 10));
        HBox fieldsBox = new HBox(10);
        fieldsBox.setPadding(new Insets(10, 10, 10, 10));
        fieldsBox.setAlignment(Pos.CENTER_LEFT);

        TextField qty = new TextField();
        qty.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                qty.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
        qty.setMaxWidth(50);

        TextField loc = new TextField();
        loc.setMaxWidth(100);

        fieldsBox.getChildren().addAll(
                new Label("QTY:"), qty,
                new Label("LOC:"), loc
        );

        logs = new TextArea();
        logs.setEditable(false);
        Button mergeButton = new Button("Merge");
        mergeButton.setOnAction(e -> BackController.merge(mergeButton, logs, qty.getText(), loc.getText()));
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

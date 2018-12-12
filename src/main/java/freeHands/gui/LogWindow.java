package freeHands.gui;


import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogWindow {
    private TextArea logsText;
    private Stage window;
    private Button saveButton;


    public LogWindow() {
        window = new Stage();
        window.initOwner(Main.mainWindow);
        VBox layout = new VBox(10);
        layout.setAlignment(Pos.CENTER);
        SimpleDateFormat sdf = new SimpleDateFormat("MM_dd_HH_mm_ss");
        Date startDate = new Date();
        String startTime = sdf.format(startDate);
        logsText = new TextArea("Start: " + startDate + "\n");
        VBox.setVgrow(logsText, Priority.ALWAYS);
        logsText.setEditable(false);
        saveButton = new Button("Save");
        saveButton.requestFocus();
        saveButton.setOnAction(e -> {
            try {
                FileUtils.writeStringToFile(new File(Main.auth.getProperty("logsBackUp") +
                                "Log_" + startTime + "--" + sdf.format(new Date()) + ".fhlog"),
                        logsText.getText(), StandardCharsets.UTF_8);
            } catch (IOException e1) {
                Platform.runLater(() -> Main.controller.addExceptionLog(e1, Thread.currentThread().getName()));
            }
        });
        layout.getChildren().addAll(logsText, saveButton);
        Scene scene = new Scene(layout, 500, 500);
        KeyCombination kc = new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN);
        Runnable action = () -> Main.controller.showHideLogs();
        scene.getAccelerators().put(kc, action);
        window.setScene(scene);
    }

    public void addLog(String log) {
        String date = new Date().toString();
        logsText.appendText(date.substring(0, date.indexOf("IST") - 1) + ": " + log + "\n");
        if (logsText.getText().length() > 10000) {
            save();
            logsText.clear();
            logsText.appendText("Start: " + new Date() + "\n");
        }
    }

    public void addExceptionLog(Exception e, String thread) {
        StringBuilder res = new StringBuilder("\n");
        res.append("Exception:\n");
        res.append(e.toString()).append(" in ").append(thread).append("\n");
        res.append(e.getCause()).append(" ").append(e.getMessage()).append("\n");
        for (StackTraceElement s : e.getStackTrace()) {
            res.append(s).append("\n");
        }
        addLog(res.toString());
    }

    public void show() {
        window.show();
    }

    public void hide() {
        window.hide();
    }

    public boolean isShowing() {
        return window.isShowing();
    }

    public void save() {
        saveButton.fire();
    }
}

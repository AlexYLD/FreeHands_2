package freeHands.gui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class LoadingWindow {
    private static Stage stage = new Stage();
    private static Timeline timeline;
    private static Label label = new Label();
    private static boolean first = true;

    public static void show(String text) {
        if (first) {
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setAlwaysOnTop(true);
            VBox layout = new VBox();
            layout.getChildren().addAll(label);

            Scene scene = new Scene(layout, 150, 35);
            layout.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 10");
            scene.getStylesheets().add("/myStyle.css");
            stage.setScene(scene);
            stage.initStyle(StageStyle.UNDECORATED);
            first = false;

        }
        setText(text);
        stage.show();
        timeline.play();
    }

    private static void setText(String text) {
        label.setText(text);
        timeline = new Timeline(
                new KeyFrame(Duration.ZERO, event -> {
                    String statusText = label.getText();
                    label.setText(
                            ((text + " . . .").equals(statusText))
                                    ? text + " ."
                                    : statusText + " ."
                    );
                }),
                new KeyFrame(Duration.millis(500))
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public static void close() {
        stage.hide();
        timeline.stop();
    }
}

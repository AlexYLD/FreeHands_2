package freeHands.gui;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Getter;

import java.util.List;

@Getter
public class WarningsWindow {
    Stage window;
    Scene scene;

    public WarningsWindow(List<String> warnings, String title) {
        window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setAlwaysOnTop(true);
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10, 10, 10, 10));
        layout.setAlignment(Pos.TOP_CENTER);
        Label label = new Label("WARNING:");
        ListView<String> listView = new ListView<>();
        listView.setItems((ObservableList<String>) warnings);
        Button button = new Button("Close");
        button.setOnAction(e -> window.close());
        button.setMaxWidth(700);
        layout.getChildren().addAll(label, listView, button);
        scene = new Scene(layout, 700, 300);
        window.setTitle(title);
        window.setScene(scene);
    }

    public void display() {
        window.show();
    }

}

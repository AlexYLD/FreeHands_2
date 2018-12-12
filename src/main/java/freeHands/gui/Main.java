//Created by Alexey Yarygin
package freeHands.gui;

import freeHands.controller.BackController;
import freeHands.controller.FrontController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Properties;

/*
Only initializing of JavaFX GUI in this class.
GUI related logic is in FrontController
Inner logic is in BackController
*/
public class Main extends Application {
    public static Properties auth = new Properties();
    public static FrontController controller;
    public static Stage mainWindow;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/sample.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        primaryStage.setTitle("Free Hands 2.0");
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(700);
        primaryStage.getIcons().add(new Image(Main.class.getResourceAsStream("/Hand.jpg")));
        Scene scene = new Scene(root, 700, 500);
        KeyCombination kc = new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN);
        Runnable action = () -> controller.showHideLogs();
        scene.getAccelerators().put(kc, action);
        scene.getStylesheets().add("/myStyle.css");
        mainWindow = primaryStage;
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(e -> {
            controller.saveLogs();
            BackController.exit();
        });

    }


    public static void main(String[] args) throws IOException {
        auth.load(Main.class.getResourceAsStream("/application.properties"));
        launch(args);
    }
}

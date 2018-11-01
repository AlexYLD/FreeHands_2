//Created by Alexey Yarygin
package freeHands.gui;

import freeHands.controller.BackController;
import freeHands.controller.FrontController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Properties;

/*
Only initializing of JavaFX GUI in this class.
GUI related logic is in FrontController
Inner logic is in BackController
*/
public class Main extends Application {
    public static Properties auth = new Properties();
    public static FrontController controller;

    @Override
    public void start(Stage primaryStage) throws Exception {
        auth.load(Main.class.getResourceAsStream("/application.properties"));
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/sample.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        primaryStage.setTitle("Free Hands 2.0");
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(700);
        primaryStage.getIcons().add(new Image(Main.class.getResourceAsStream("/Hand.jpg")));
        Scene scene = new Scene(root, 700, 500);
        scene.getStylesheets().add("/myStyle.css");
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(e -> BackController.exit());

    }


    public static void main(String[] args) {
        launch(args);
    }
}

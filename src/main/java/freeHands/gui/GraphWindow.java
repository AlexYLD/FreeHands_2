//Created by Alexey Yarygin
package freeHands.gui;

import freeHands.controller.BackController;
import freeHands.entity.ItuffObject;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.Getter;

import java.util.*;

@Getter
public class GraphWindow {

    private StackedBarChart<String, Number> graph;
    private List<String> hosts;
    private int yield;
    private Stage window;
    private GridPane failTable;
    //Map<bin,Map<host,binCount>>
    public static Map<String, Map<String, Integer>> binsPerHost;

    public GraphWindow(List<String> hosts, String title, String yield) {
        this.hosts = hosts;
        this.yield = yield.equals("") || yield == null ? 90 : Integer.parseInt(yield);
        binsPerHost = new HashMap<>();
        VBox vBox = new VBox(10);
        failTable = new GridPane();
        ScrollPane scrollPane = new ScrollPane(failTable);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        failTable.setAlignment(Pos.CENTER);
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis(0, 100, 20);
        xAxis.setCategories(FXCollections.observableArrayList(hosts));
        yAxis.setTickUnit(10);
        yAxis.setAnimated(true);
        graph = new StackedBarChart<>(xAxis, yAxis);
        XYChart.Series<String, Number> series1 = new XYChart.Series<>();
        for (String host : hosts) {
            XYChart.Data<String, Number> dataPass = new XYChart.Data<>(host, 0);
            series1.getData().add(dataPass);
        }
        graph.getData().addAll(series1);
        graph.setLegendVisible(false);
        refillGraphData();


        vBox.getChildren().addAll(graph, scrollPane);

        Scene scene = new Scene(vBox, scrollPane.getPrefWidth(), graph.getMaxHeight() + scrollPane.getPrefHeight());
        scene.getStylesheets().add("/graphStile.css");
        window = new Stage();
        window.setTitle(title);
        window.setScene(scene);
    }

    public void display() {
        window.showAndWait();
    }

    public void hide() {
        window.hide();
    }


    public void refillGraphData() {
        for (XYChart.Series<String, Number> series : graph.getData()) {
            for (XYChart.Data<String, Number> data : series.getData()) {
                long total = 0;
                long pass = 0;
                for (ItuffObject ituff : BackController.ituffs.get(data.getXValue().toLowerCase())) {
                    if (!ituff.getFileName().equals("comment")) {
                        total++;
                        if (ituff.getBin().equals("PASS")) {
                            pass++;
                        }
                    }
                }
                Double per = pass * 100.00 / total;
                if (Double.isNaN(per)) {
                    per = 0.;
                }
                data.setYValue(per);
                Node node = data.getNode();
                if (node != null) {
                    if (per >= yield) {
                        node.setStyle("-fx-bar-fill: green;");
                    } else {
                        node.setStyle("-fx-bar-fill: orange;");
                    }
                }
            }
        }


        failTable.getChildren().clear();
        Label tempLabel;

        tempLabel = new Label("Bin\\Host");
        tempLabel.getStyleClass().add("name-Label");
        GridPane.setHgrow(tempLabel, Priority.ALWAYS);
        failTable.add(tempLabel, 0, 0);

        tempLabel = new Label("SUM");
        tempLabel.getStyleClass().add("name-Label");
        tempLabel.setStyle("-fx-background-color: red");
        GridPane.setHgrow(tempLabel, Priority.ALWAYS);
        failTable.add(tempLabel, 1, 0);

        int row = 1;
        for (String bin : binsPerHost.keySet()) {
            tempLabel = new Label(bin);
            tempLabel.getStyleClass().add("name-Label");
            GridPane.setHgrow(tempLabel, Priority.ALWAYS);
            failTable.add(tempLabel, 0, row++);
        }


        int column = 2;
        for (String host : hosts) {
            tempLabel = new Label(host);
            tempLabel.getStyleClass().add("name-Label");
            GridPane.setHgrow(tempLabel, Priority.ALWAYS);
            failTable.add(tempLabel, column++, 0);
        }

        row = 1;
        int sum = 0;
        for (String bin : binsPerHost.keySet()) {
            Map<String, Integer> hostBinCount = binsPerHost.get(bin);
            column = 2;
            for (String host : hosts) {
                Integer val = hostBinCount.get(host.toLowerCase());
                if (val == null) {
                    val = 0;
                }
                sum += val;
                String count = String.valueOf(val);
                tempLabel = new Label(count);
                tempLabel.getStyleClass().add("bin-Label");
                failTable.add(tempLabel, column, row);
                column++;
            }
            tempLabel = new Label(String.valueOf(sum));
            tempLabel.getStyleClass().add("bin-Label");
            failTable.add(tempLabel, 1, row);
            sum = 0;
            row++;
        }

    }


}




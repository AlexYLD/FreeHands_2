package freeHands.controller;

import freeHands.entity.ItuffObject;
import freeHands.gui.GraphWindow;
import freeHands.gui.Main;
import freeHands.gui.MergeWindow;
import freeHands.gui.WarningsWindow;
import javafx.collections.ListChangeListener;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;


public class FrontController implements Initializable {

    public GridPane listsBox;
    public GridPane statsBox;
    public GridPane checkingBox;
    public HBox hostsBox;
    public Button warningButton;
    public Button connectButton;
    public Button clearButton;
    public Button submitButton;
    public Button saveButton;
    public Button refreshButton;
    public Button showGraphButton;
    public Button mergeButton;
    public ComboBox<String> commentHost;
    public ComboBox<SetUp> cellChooser;
    public ComboBox<Integer> fromHour;
    public ComboBox<Integer> fromMin;
    public ComboBox<Integer> toHour;
    public ComboBox<Integer> toMin;
    public TextField manualText;
    public TextField lotText;
    public TextField sumText;
    public TextField commentText;
    public Label settingsLabel;
    public Label passLabel;
    public Label failLabel;
    public Label totalLabel;
    public Label statsDescLabel;
    public CheckBox checkingV;
    public CheckBox allDatesV;
    public CheckBox prodModeV;
    public ListView<String> statsDesc;
    public DatePicker fromDate;
    public DatePicker toDate;
    public Region upTopReg;
    public Region downTopReg;
    private GraphWindow graph;
    private WarningsWindow warningsWindow;
    private MergeWindow mergeWindow;


    public void initialize(URL location, ResourceBundle resources) {
        File setUpsFolder = new File(Main.auth.getProperty("setUpsFolder"));
        Arrays.stream(setUpsFolder.listFiles()).forEach(f -> cellChooser.getItems().add(new SetUp(f.getPath())));
        cellChooser.getSelectionModel().select(0);
        cellChanged();

        downTopReg.maxWidthProperty().bind(upTopReg.widthProperty());
        manualText.maxWidthProperty().bind(cellChooser.widthProperty().add(connectButton.widthProperty()).add(settingsLabel.widthProperty()).add(10));

        for (int i = 0; i < 60; i++) {
            fromMin.getItems().add(i);
            toMin.getItems().add(i);
            if (i < 24) {
                fromHour.getItems().add(i);
                toHour.getItems().add(i);
            }
        }
        fromHour.getSelectionModel().select(0);
        toHour.getSelectionModel().select(23);
        fromMin.getSelectionModel().select(0);
        toMin.getSelectionModel().select(59);
        fromDate.setValue(LocalDate.now());
        toDate.setValue(LocalDate.now());


        cellChooser.setOnAction(e -> cellChanged());
        checkingV.setOnAction(e -> applyChecking());
        allDatesV.setOnAction(e -> saveAll());
        connectButton.setOnAction(e -> connect());
        clearButton.setOnAction(e -> stopProcesses());
        warningButton.setOnAction(e -> showWarnings());
        submitButton.setOnAction(e -> addComment());
        saveButton.setOnAction(e -> saveExcel());
        refreshButton.setOnAction(e -> refresh());
        showGraphButton.setOnAction(e -> showGraph());
        mergeButton.setOnAction(e -> showMergeWindow());

    }

    void showMergeWindow() {
        if (!mergeWindow.getWindow().isShowing()) {
            mergeWindow.display();
        } else {
            mergeWindow.hide();
        }
    }

    private void showGraph() {
        if (graph.getWindow().isShowing()) {
            graph.hide();
        } else {
            graph.display();
        }
    }

    private void refresh() {
        clearButton.fire();
        connectButton.fire();
    }

    private void saveExcel() {
        saveButton.setDisable(true);
        BackController.saveExcel(commentHost.getItems());
        saveButton.setDisable(false);
        if (checkingV.isSelected() && BackController.warnings.isEmpty()) {
            mergeButton.setDisable(false);
        }
    }

    private void addComment() {
        if (!commentText.getText().equals("")) {
            String host = commentHost.getSelectionModel().getSelectedItem().toLowerCase();
            BackController.addComment(host, commentText.getText());

        }
    }

    synchronized void showWarnings() {
        if (!warningsWindow.getWindow().isShowing()) {
            warningsWindow.display();
        }
    }


    void stopProcesses() {
        if (graph.getWindow().isShowing()) {
            showGraph();
        }
        graph = new GraphWindow(new ArrayList<>(), "empty");
        BackController.stopProcesses();
        warningButton.getStyleClass().add("warning-red");
        listsBox.getChildren().clear();
        statsBox.getChildren().clear();
        statsBox.getChildren().addAll(statsDescLabel, statsDesc);
        commentHost.getItems().clear();
        warningButton.getStyleClass().removeAll("warning-red", "warning-green");
        warningButton.getStyleClass().add("warning-green");
        hostsBox.setDisable(false);
        checkingBox.setDisable(false);
        submitButton.setDisable(true);
        warningButton.setDisable(true);
        commentHost.setDisable(true);
        commentText.setDisable(true);
        saveButton.setDisable(true);
        refreshButton.setDisable(true);
        showGraphButton.setDisable(true);
        clearButton.setDisable(true);
        mergeButton.setDisable(true);
        failLabel.setText("0");
        passLabel.setText("0");
        totalLabel.setText("0");
    }

    Button getWarningButton() {
        return warningButton;
    }

    @SneakyThrows
    private void connect() {
        LocalDateTime fromDateTime = LocalDateTime.now();
        LocalDateTime toDateTime = LocalDateTime.now();
        if (!allDatesV.isSelected()) {
            LocalDate date = fromDate.getValue();
            fromDateTime = LocalDateTime.of(
                    date.getYear(), date.getMonth(), date.getDayOfMonth(),
                    fromHour.getSelectionModel().getSelectedItem(), fromMin.getSelectionModel().getSelectedItem());
            date = toDate.getValue();
            toDateTime = LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(),
                    toHour.getSelectionModel().getSelectedItem(), toMin.getSelectionModel().getSelectedItem());
        }

        List<String> hosts;
        if (cellChooser.getSelectionModel().getSelectedItem().getName().equals("Manual.txt")) {
            hosts = Arrays.asList(manualText.getText().toLowerCase().split(";"));
        } else {
            hosts = cellChooser.getSelectionModel().getSelectedItem().getHosts();
        }
        int i = 0;
        hostsBox.setDisable(true);
        checkingBox.setDisable(true);


        if (checkingV.isSelected()) {
            BackController.setLotNum(lotText.getText());
            BackController.setSum(sumText.getText());
        } else {
            BackController.setLotNum(null);
            BackController.setSum(null);
        }
        for (String host : hosts) {
            if (allDatesV.isSelected()) {
                BackController.connect(host);
            } else {
                BackController.connect(host, fromDateTime, toDateTime);
            }
            if (host.contains(" ")) {
                host = host.substring(0, host.indexOf(" "));
            }
            ListView<ItuffObject> listView = new ListView<>();
            listView.setFixedCellSize(20);
            GridPane.setHgrow(listView, Priority.ALWAYS);
            GridPane.setVgrow(listView, Priority.ALWAYS);
            Label label = new Label(host.toUpperCase());
            GridPane.setHgrow(label, Priority.ALWAYS);
            label.getStyleClass().add("name-Label");
            listView.setCellFactory(param -> new ItuffCell());
            listView.setItems(BackController.ituffs.get(host));
            listsBox.add(listView, i, 1);
            listsBox.add(label, i, 0);

            ListView<Object> statsList = new ListView<>();
            GridPane.setHgrow(statsList, Priority.ALWAYS);


            statsList.getItems().addAll(0, 0, 0, 0 + "%");
            statsList.getStyleClass().add("stats-List");

            DecimalFormat df = new DecimalFormat("#.##");
            listView.getItems().addListener((ListChangeListener<ItuffObject>) c -> {
                int listTotal = 0;
                int listPass = 0;
                Double listPercent;


                for (ItuffObject ituff : listView.getItems()) {
                    if (!ituff.getFileName().startsWith("comment")) {
                        listTotal++;
                        if (ituff.getBin().equals("PASS")) {
                            listPass++;
                        }
                    }
                }
                listPercent = listPass * 100. / listTotal;
                if (Double.isNaN(listPercent)) {
                    listPercent = 0.;
                }
                statsList.getItems().set(2, listTotal);
                statsList.getItems().set(0, listPass);
                statsList.getItems().set(1, listTotal - listPass);
                statsList.getItems().set(3, df.format(listPercent) + "%");

            });
            Label label1 = new Label(host.toUpperCase());
            GridPane.setHgrow(label1, Priority.ALWAYS);
            label1.getStyleClass().add("name-Label");


            statsBox.add(statsList, i + 1, 1);
            statsBox.add(label1, 1 + i++, 0);
            commentHost.getItems().add(host.toUpperCase());
        }

        graph = new GraphWindow(commentHost.getItems(), getCellName());
        graph.getWindow().initOwner(showGraphButton.getScene().getWindow());
        warningsWindow = new WarningsWindow(BackController.warnings, getCellName());
        mergeWindow = new MergeWindow();
        commentHost.getSelectionModel().select(0);
        warningButton.setDisable(false);
        submitButton.setDisable(false);
        commentHost.setDisable(false);
        commentText.setDisable(false);
        saveButton.setDisable(false);
        refreshButton.setDisable(false);
        showGraphButton.setDisable(false);
        clearButton.setDisable(false);

    }

    public String getCellName() {
        return cellChooser.getSelectionModel().getSelectedItem().toString();
    }

    void recount() {
        int totalPass = (int) BackController.listItuffs.values()
                .stream()
                .filter(i12 -> i12.getBin() != null && i12.getBin().equalsIgnoreCase("pass"))
                .count();
        totalLabel.setText(String.valueOf(BackController.listItuffs.size()));
        passLabel.setText(String.valueOf(totalPass));
        failLabel.setText(String.valueOf(BackController.listItuffs.size() - totalPass));
        graph.refillGraphData();
        mergeButton.setDisable(true);
    }

    private void saveAll() {
        fromHour.setDisable(allDatesV.isSelected());
        toHour.setDisable(allDatesV.isSelected());
        fromMin.setDisable(allDatesV.isSelected());
        toMin.setDisable(allDatesV.isSelected());
        fromDate.setDisable(allDatesV.isSelected());
        toDate.setDisable(allDatesV.isSelected());
    }

    private void applyChecking() {
        lotText.setDisable(!checkingV.isSelected());
        sumText.setDisable(!checkingV.isSelected());
    }

    private void cellChanged() {
        manualText.setDisable(!cellChooser.getSelectionModel().getSelectedItem().getName().equals("Manual.txt"));
    }

}

class SetUp extends File {

    SetUp(String s) {
        super(s);
    }

    @Override
    public String toString() {
        return getName().substring(0, getName().indexOf("."));
    }

    List<String> getHosts() {
        List<String> hosts = new ArrayList<>();
        try {
            hosts = FileUtils.readLines(this, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String host : hosts) {
            host = host.toLowerCase();
        }
        return hosts;
    }
}

class ItuffCell extends ListCell<ItuffObject> {
    @Override
    protected void updateItem(ItuffObject item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            getStyleClass().removeAll("list-cell-pass", "list-cell-fail", "list-cell-com");
            setText(null);
        } else {
            setText(item.getBin());
            if (item.getFileName().equals("comment")) {
                getStyleClass().removeAll("list-cell-pass", "list-cell-fail", "list-cell-com");
                getStyleClass().add("list-cell-com");
            } else if (item.getBin().equalsIgnoreCase("pass")) {
                getStyleClass().removeAll("list-cell-pass", "list-cell-fail", "list-cell-com");
                getStyleClass().add("list-cell-pass");
            } else {
                getStyleClass().removeAll("list-cell-pass", "list-cell-fail", "list-cell-com");
                getStyleClass().add("list-cell-fail");
            }
        }
    }
}


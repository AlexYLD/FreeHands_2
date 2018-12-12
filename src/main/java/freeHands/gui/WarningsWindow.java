//Created by Alexey Yarygin
package freeHands.gui;

import freeHands.controller.BackController;
import freeHands.entity.ItuffObject;
import freeHands.entity.WarningObject;
import freeHands.entity.WarningType;
import freeHands.model.SingleHostProcess;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.SneakyThrows;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class WarningsWindow {
    private Stage window;
    private Scene listScene;

    public WarningsWindow(List<WarningObject> warnings, String title) {
        window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setAlwaysOnTop(true);
        //init list scene
        VBox listLayout = new VBox(10);
        listLayout.setPadding(new Insets(10, 10, 10, 10));
        listLayout.setAlignment(Pos.TOP_CENTER);
        Label label = new Label("WARNING:");
        ListView<WarningObject> listView = new ListView<>();
        SortedList<WarningObject> sortedList = new SortedList<>((ObservableList<WarningObject>) warnings);
        sortedList.setComparator((w1, w2) -> {
            int host1 = Integer.parseInt(w1.getBadItuffs().get(0).getHost().replaceAll("[\\D]", ""));
            int host2 = Integer.parseInt(w2.getBadItuffs().get(0).getHost().replaceAll("[\\D]", ""));
            return Integer.compare(host1, host2);
        });
        listView.setItems(sortedList);

        listView.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY)) {
                if (event.getClickCount() >= 2) {
                    WarningObject item = listView.getSelectionModel().getSelectedItem();
                    if (item != null) {
                        showRepairScene(item);
                    }
                }
            }
        });
        Button button = new Button("Close");
        button.setOnAction(e -> window.close());
        button.setMaxWidth(700);
        listLayout.getChildren().addAll(label, listView, button);
        listScene = new Scene(listLayout, 700, 300);
        //--------------------------------------------------------------------------------
        window.setOnHidden(e -> window.setScene(listScene));
        window.setTitle(title);
        window.setScene(listScene);
    }

    private void showRepairScene(WarningObject warning) {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10, 10, 10, 10));
        WarningType warningType = warning.getWarningType();
        Label title = new Label(warningType + ":");
        layout.getChildren().add(title);
        Button backButton = new Button("Back");
        backButton.setOnAction(e -> {
                    window.setScene(listScene);
                }
        );
        Button deleteButton = new Button("Delete");
        HBox buttons = new HBox(10);
        buttons.getChildren().add(deleteButton);
        if (warningType.equals(WarningType.DUPLICATES)) {
            Map<CheckBox, ItuffObject> checkBoxes = new HashMap<>();
            for (ItuffObject ituff : warning.getBadItuffs()) {
                CheckBox checkBox = new CheckBox(ituff.getFileName() + "(" + ituff.getBin() + ") on " + ituff.getHost());
                checkBox.setOnAction(e -> {
                    int unselectCount = (int) checkBoxes.keySet().stream()
                            .filter(cb -> !cb.isSelected())
                            .count();
                    if (unselectCount == 0) {
                        checkBoxes.keySet().stream()
                                .filter(cb -> cb.isSelected() && !cb.equals(e.getSource()))
                                .findFirst()
                                .ifPresent(cb -> cb.setSelected(false));
                    } else if (unselectCount > 1) {
                        checkBoxes.keySet().stream()
                                .filter(cb -> !cb.isSelected() && !cb.equals(e.getSource()))
                                .findFirst()
                                .ifPresent(cb -> cb.setSelected(true));
                    }
                });
                checkBoxes.put(checkBox, ituff);
            }
            int counter = 0;

            for (CheckBox checkBox : checkBoxes.keySet()) {
                checkBox.setSelected(true);
                if (++counter >= checkBoxes.size() - 1) {
                    break;
                }
            }

            deleteButton.setText("Delete Selected");
            deleteButton.setOnAction(e -> {
                SingleHostProcess process = null;
                for (CheckBox checkBox : checkBoxes.keySet()) {
                    if (checkBox.isSelected()) {
                        ItuffObject ituffObject = checkBoxes.get(checkBox);
                        if (process == null || !process.getExistNames().contains(ituffObject.getFileName())) {
                            process = BackController.getProcess(ituffObject.getFileName());
                        }
                        process.removeAll(Stream.of(ituffObject.getFileName()).collect(Collectors.toSet()));
                    }
                }
                backButton.fire();
            });
            layout.getChildren().addAll(checkBoxes.keySet());

        } else {
            ItuffObject badItuff = warning.getBadItuffs().get(0);
            Label fileNameLabel = new Label(badItuff.getFileName() + " on " + badItuff.getHost());
            layout.getChildren().add(fileNameLabel);
            SingleHostProcess hostProcess = BackController.getProcess(badItuff.getFileName());
            if (hostProcess == null) {
                Platform.runLater(() -> Main.controller.addLog("Can't find ituff"));
                return;
            }
            deleteButton.setOnAction(e -> {
                boolean deleted = hostProcess.removeAll(Stream.of(badItuff.getFileName()).collect(Collectors.toSet()));
                if (deleted) {
                    Platform.runLater(() -> Main.controller.addLog(badItuff.getFileName() + " on " + hostProcess.getName() + " was deleted"));
                    backButton.fire();
                    if (warningType.equals(WarningType.EMPTY_FILE)) {
                        Platform.runLater(() -> Main.controller.removeWarning(warning));
                    }
                }
            });
            if (!warningType.equals(WarningType.EMPTY_FILE)) {

                Button repairButton = new Button("Repair");
                TextField binToRepairText = new TextField();

                if (warningType.equals(WarningType.DAMAGED_FILE)) {
                    repairButton.setDisable(true);
                    binToRepairText.setPromptText("Type BIN that need to be added");
                    binToRepairText.textProperty().addListener((observable, oldValue, newValue) -> {
                        if (!newValue.matches("[\\d]+\\.[\\d]+")) {
                            repairButton.setDisable(true);
                            return;
                        }
                        repairButton.setDisable(false);
                    });
                    layout.getChildren().add(binToRepairText);
                } else if (warningType.equals(WarningType.GOLDEN_RESULT)) {
                    repairButton.setText("Set to regular");
                } else {
                    repairButton.setText("Set to current value");
                }

                repairButton.setOnAction(e -> {
                    String fullFileName = Main.auth.getProperty("remoteFolder") + badItuff.getFileName();
                    String ituffText = hostProcess.getSingleItuffText(fullFileName);
                    String newItuffText = null;
                    if (warningType.equals(WarningType.DAMAGED_FILE)) {
                        try {
                            newItuffText = repairItuff(ituffText, badItuff.getDate(), binToRepairText.getText());
                        } catch (Exception ex) {
                            binToRepairText.setDisable(true);
                            repairButton.setDisable(true);
                            binToRepairText.setText("Too damaged to repair");
                            return;
                        }

                        if (newItuffText == null) {
                            binToRepairText.setDisable(true);
                            repairButton.setDisable(true);
                            binToRepairText.setText("Too short to repair");
                            return;
                        }

                    } else if (warningType.equals(WarningType.LOT_MISMATCH)) {
                        newItuffText = ituffText.replaceAll("_lotid_[\\d\\w]+\n", "_lotid_" + Main.controller.lotText.getText() + "\n");
                    } else if (warningType.equals(WarningType.SUM_MISMATCH)) {
                        newItuffText = ituffText.replaceAll("_smrynam_[\\d\\w]+\n", "_smrynam_" + Main.controller.sumText.getText() + "\n");
                    } else {
                        newItuffText = ituffText.replaceAll("golden", "regular");
                    }
                    boolean fixed = hostProcess.setItuffText(fullFileName, newItuffText);
                    if (fixed) {
                        hostProcess.removeItuff(badItuff.getFileName());
                        hostProcess.addItuff(new ItuffObject(badItuff.getFileName(), newItuffText),
                                hostProcess.getName().equals(badItuff.getHost()));
                        Platform.runLater(() -> Main.controller.addLog(warning.toString() + " " + "Repaired"));
                        backButton.fire();
                        return;
                    }
                    Platform.runLater(() -> Main.controller.addLog("Repair failed"));
                });

                buttons.getChildren().addAll(repairButton);

            }
        }
        layout.getChildren().addAll(buttons, backButton);
        Scene scene = new Scene(layout, 700, 300);
        backButton.requestFocus();
        window.setScene(scene);
    }

    @SneakyThrows
    private String repairItuff(String ituffText, Date ituffDate, String newBin) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String endDate = sdf.format(ituffDate) + (100000 + new Random().nextInt(899999));

        int startIndex = ituffText.indexOf("_begindt_") + "_begindt_".length();
        Date startDate = sdf.parse(ituffText.substring(startIndex, ituffText.indexOf("\n", startIndex) - 6));
        String lsep = "2_lsep";
        if (!ituffText.contains(lsep)) {
            return null;
        }
        String lend = "2_lend";
        StringBuilder repairedText = new StringBuilder(ituffText);
        if (!ituffText.contains(lend)) {
            repairedText.replace(ituffText.lastIndexOf(lsep), ituffText.lastIndexOf(lsep) + lsep.length(), lend);
        }
        repairedText.replace(repairedText.lastIndexOf(lend) + lend.length(), repairedText.length() - 1, "");
        repairedText.append("3_ttime_").append((ituffDate.getTime() - startDate.getTime()) / 1000).append("\n");
        repairedText.append("3_curfbin_").append(newBin, newBin.indexOf(".") + 1, newBin.length()).append("\n");
        repairedText.append("3_curibin_").append(newBin, 0, newBin.indexOf(".")).append("\n");
        repairedText.append("3_trslt_fail").append("\n").append("3_lend\n4_total_1\n");
        repairedText.append("4_enddate_").append(endDate).append("\n");
        repairedText.append("4_lend\n").append("5_lend\n").append("6_lend\n").append("7_lend\n");
        return repairedText.toString();
    }

    public void display() {
        Platform.runLater(() -> window.show());
    }

}

//Created by Alexey Yarygin
package freeHands.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.*;
import freeHands.entity.CommentObj;
import freeHands.entity.ItuffObject;
import freeHands.entity.WarningObject;
import freeHands.entity.WarningType;
import freeHands.gui.GraphWindow;
import freeHands.gui.LoadingWindow;
import freeHands.gui.Main;
import freeHands.model.SingleHostProcess;
import javafx.application.Platform;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TextArea;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCreationHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackController {
    public static Map<String, SortedList<ItuffObject>> ituffs = new HashMap<>();//<Hostname, list of ituffs>
    static Map<String, ItuffObject> listItuffs = new HashMap<>();//<file name, ituff object>

    private static Set<SingleHostProcess> processes = new HashSet<>();//set of threads for each host

    private static List<WarningObject> nonGuiWarnings = new ArrayList<>();

    private static String lotNum;
    private static String sum;

    static void setLotNum(String lotNum) {
        BackController.lotNum = lotNum;
    }

    static void setSum(String sum) {
        BackController.sum = sum;
    }

    static void connect(String host) {
        SingleHostProcess process = new SingleHostProcess(host);
        addProcess(process);
    }

    public static void addWarning(WarningObject warning) {
        synchronized (BackController.class) {
            nonGuiWarnings.add(warning);
        }
        Platform.runLater(() -> Main.controller.addWarning(warning));
    }


    public static SingleHostProcess getProcess(String ituffName) {
        return processes.stream().filter(p -> p.getExistNames().contains(ituffName)).findFirst().orElse(null);
    }

    static void connect(String host, LocalDateTime fromDateTime, LocalDateTime toDateTime) {
        SingleHostProcess process = new SingleHostProcess(host, fromDateTime, toDateTime);
        addProcess(process);
    }

    //Adding list for each host and starting process
    private static void addProcess(SingleHostProcess process) {
        SortedList<ItuffObject> sortedList = new SortedList<>(process.getItuffs());
        sortedList.setComparator(ItuffObject::compareTo);
        ituffs.put(process.getName(), sortedList);
        processes.add(process);
        process.setDaemon(true);
        process.start();
    }

    @SneakyThrows
    static void addComment(String host, String text) {
        ObjectMapper mapper = new ObjectMapper();
        for (SingleHostProcess process : processes) {
            if (process.getName().equalsIgnoreCase(host)) {
                CommentObj commentObj = new CommentObj(text, host);
                process.addItuff(commentObj, false);
                String path;
                //Storing comment
                path = Main.auth.getProperty("commentsFolder") + host.toLowerCase();
                new File(path).mkdirs();
                mapper.writeValue(new File(path + "/comment" + commentObj.getHost() + System.currentTimeMillis()), commentObj);
                return;
            }
        }
    }

    @SneakyThrows
    static void stopProcesses() {
        for (SingleHostProcess process : processes) {
            process.interrupt();
            process.myInterrupt();
        }
        nonGuiWarnings.clear();
        ituffs.clear();
        listItuffs.clear();
        processes.clear();
        // FileUtils.write(new File(Main.auth.getProperty("authorized_keys")), "", false);
    }

    @SneakyThrows
    public static void exit() {
        for (SingleHostProcess process : processes) {
            process.interrupt();
            process.myInterrupt();
        }
        for (SingleHostProcess process : processes) {
            while (process.isAlive()) ;
        }
    }

    //POI API
    @SneakyThrows
    static void saveExcel(List<String> hosts) {
        File binTableFolder = new File(Main.auth.getProperty("binTableFolder"));
        InputStream fis = null;
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        Workbook workbook = null;
        for (File file : binTableFolder.listFiles()) {
            if (file.getName().startsWith("BinTable_" + Main.controller.getCellName().replace(" ", "_") + "_" + today)) {
                fis = new FileInputStream(file);
                workbook = new XSSFWorkbook(fis);
                break;
            }
        }
        if (workbook == null) {
            workbook = new XSSFWorkbook();
        }


        Sheet sheetBins;
        String sheetName = lotNum + "-" + sum;


        while (workbook.getSheet(sheetName) != null) {
            sheetName = sheetName + "-copy";
        }
        sheetBins = workbook.createSheet(sheetName);
        CreationHelper factory = workbook.getCreationHelper();
        Drawing drawing = sheetBins.createDrawingPatriarch();
        Comment cellComment;

        Map<String, CellStyle> styles = getStyles(workbook);

        //Rows... cells... excitement (o_O) ...
        int columnNum = 1;
        int rowNum;
        List<ItuffObject> list;
        List<String> range = new ArrayList<>();
        Row firstRow = sheetBins.createRow(0);
        Cell titleCell;
        for (String host : hosts) {
            list = ituffs.get(host.toLowerCase());

            titleCell = firstRow.createCell(columnNum);
            if (!range.contains(titleCell.getAddress().toString().substring(0, 1))) {
                range.add(titleCell.getAddress().toString().substring(0, 1));
            }
            titleCell.setCellValue(host);
            titleCell.setCellStyle(styles.get("titleStyle"));

            titleCell = firstRow.createCell(columnNum + 1);
            titleCell.setCellValue(host + "\nComment");
            titleCell.setCellStyle(styles.get("titleStyle"));

            sheetBins.autoSizeColumn(columnNum);
            sheetBins.autoSizeColumn(columnNum + 1);

            for (rowNum = 1; rowNum <= list.size(); rowNum++) {
                Row row = sheetBins.getRow(rowNum) == null ? sheetBins.createRow(rowNum) : sheetBins.getRow(rowNum);
                Cell cell;
                cell = row.createCell(columnNum);
                String fileName = list.get(rowNum - 1).getFileName();
                if (fileName.equals("comment")) {
                    cell.setCellStyle(styles.get("yellowStyle"));
                    cell = row.createCell(columnNum + 1);
                }

                cell.setCellType(CellType.STRING);
                cell.setCellValue(list.get(rowNum - 1).getBin());

                cellComment = drawing.createCellComment(factory.createClientAnchor());
                cellComment.setString(factory.createRichTextString(list.get(rowNum - 1).getDate().toString()));
                cell.setCellComment(cellComment);

                if (cell.getStringCellValue().equals("PASS")) {
                    cell.setCellStyle(styles.get("greenStyle"));
                } else if (!fileName.equals("comment")) {
                    cell.setCellStyle(styles.get("redStyle"));
                } else {
                    cell.setCellStyle(styles.get("regularStyle"));
                }
            }
            columnNum += 2;
        }

        List<String> bins = listItuffs.values().stream()
                .map(ItuffObject::getBin)
                .filter(b -> !b.equals("PASS"))
                .distinct().collect(Collectors.toList());

        Row nameRow = sheetBins.getRow(21) == null ? sheetBins.createRow(21) : sheetBins.getRow(21);
        Row passRow = sheetBins.getRow(22) == null ? sheetBins.createRow(22) : sheetBins.getRow(22);
        Row failRow = sheetBins.getRow(23) == null ? sheetBins.createRow(23) : sheetBins.getRow(23);
        Row perRow = sheetBins.getRow(24) == null ? sheetBins.createRow(24) : sheetBins.getRow(24);

        Cell tempCell = nameRow.createCell(19);
        tempCell.setCellValue("SUM");
        tempCell.setCellStyle(styles.get("sumStyle"));

        tempCell = passRow.createCell(18);
        tempCell.setCellValue("Pass");
        tempCell.setCellStyle(styles.get("greenStyle"));

        tempCell = failRow.createCell(18);
        tempCell.setCellValue("Fail");
        tempCell.setCellStyle(styles.get("redStyle"));

        tempCell = perRow.createCell(18);
        tempCell.setCellValue("%");
        tempCell.setCellStyle(styles.get("percentStyle"));

        tempCell = perRow.createCell(18 + hosts.size() + 2);
        tempCell.setCellValue("Total %");
        tempCell.setCellStyle(styles.get("titleStyle"));


        for (int i = 20; i < hosts.size() + 20; i++) {
            tempCell = nameRow.createCell(i);
            tempCell.setCellValue(hosts.get(i - 20).toLowerCase());
            tempCell.setCellStyle(styles.get("titleStyle"));

            tempCell = passRow.createCell(i);
            tempCell.setCellFormula("COUNTIF(" + range.get(i - 20) + ":" + range.get(i - 20) + ",\"Pass\")");
            tempCell.setCellStyle(styles.get("greenStyle"));

            tempCell = failRow.createCell(i);
            tempCell.setCellFormula("COUNTA(" + range.get(i - 20) + ":" + range.get(i - 20) + ")-1-" + passRow.getCell(i).getAddress().toString());
            tempCell.setCellStyle(styles.get("redStyle"));

            tempCell = perRow.createCell(i);
            tempCell.setCellStyle(styles.get("percentStyle"));
            tempCell.setCellFormula(passRow.getCell(i).getAddress().toString() + "/(" + passRow.getCell(i).getAddress().toString() + "+" + failRow.getCell(i).getAddress().toString() + ")");
        }

        //fill fails
        for (int i = 25; i < 25 + bins.size(); i++) {
            for (int j = 20; j < hosts.size() + 20; j++) {
                Row row = sheetBins.getRow(i) == null ? sheetBins.createRow(i) : sheetBins.getRow(i);
                tempCell = row.createCell(j);
                tempCell.setCellFormula("COUNTIF(" + range.get(j - 20) + ":" + range.get(j - 20) + ",S" + (i + 1) + ")");
                tempCell.setCellStyle(styles.get("regularStyle"));
            }
            tempCell = sheetBins.getRow(i).createCell(18);
            tempCell.setCellStyle(styles.get("regularStyle"));
            tempCell.setCellValue(bins.get(i - 25));
        }


        //sum of pass/fail/fail bins
        for (int i = 22; i < 22 + bins.size() + 3; i++) {
            Row row = sheetBins.getRow(i) == null ? sheetBins.createRow(i) : sheetBins.getRow(i);
            if (row.equals(perRow)) {
                continue;
            }
            tempCell = row.createCell(19);
            tempCell.setCellFormula("SUM(U" + (i + 1) + ":" + row.getCell(20 + range.size() - 1).getAddress().toString() + ")");
            tempCell.setCellStyle(styles.get("sumStyle"));
            if (i > 24) {
                Cell perCell;
                perCell = row.createCell(19 + hosts.size() + 1);
                perCell.setCellStyle(styles.get("percentStyle"));
                perCell.setCellFormula(tempCell.getAddress().toString() + "/T24");
            }
        }


        tempCell = perRow.createCell(19);
        tempCell.setCellFormula("T23/(T24+T23)");
        tempCell.setCellStyle(styles.get("percentStyle"));

        if (fis != null) {
            fis.close();
        }
        String binTableName = Main.auth.getProperty("binTableFolder") + "BinTable_" + Main.controller.getCellName().replace(" ", "_") + "_" + today + ".xlsx";
        File excelFile = new File(binTableName);
        OutputStream outputStream = new FileOutputStream(excelFile);
        workbook.write(outputStream);
        outputStream.close();

        for (String host : hosts) {
            File directory = new File(Main.auth.getProperty("commentsFolder") + host.toLowerCase());
            if (directory.exists()) {
                for (File file : directory.listFiles()) {
                    file.renameTo(new File(Main.auth.getProperty("commentsFolder") + "BackUp/" + file.getName()));
                }
                directory.delete();
            }
        }
    }

    //Styles for excel.
    private static Map<String, CellStyle> getStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();
        Font whitFont = workbook.createFont();
        whitFont.setColor(IndexedColors.WHITE.index);
        whitFont.setBold(true);

        Font blackFont = workbook.createFont();
        blackFont.setColor(IndexedColors.BLACK.index);
        blackFont.setBold(true);

        CellStyle tempStyle = workbook.createCellStyle();
        tempStyle.setAlignment(HorizontalAlignment.CENTER);
        tempStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        tempStyle.setFont(blackFont);
        tempStyle.setWrapText(true);
        tempStyle.setDataFormat(new XSSFCreationHelper((XSSFWorkbook) workbook).createDataFormat().getFormat("00.00%"));
        tempStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tempStyle.setBorderBottom(BorderStyle.THIN);
        tempStyle.setBorderTop(BorderStyle.THIN);
        tempStyle.setBorderRight(BorderStyle.THICK);
        tempStyle.setBorderLeft(BorderStyle.THICK);
        styles.put("percentStyle", tempStyle);

        tempStyle = workbook.createCellStyle();
        tempStyle.setAlignment(HorizontalAlignment.CENTER);
        tempStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
        tempStyle.setFont(whitFont);
        tempStyle.setWrapText(true);
        tempStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tempStyle.setBorderBottom(BorderStyle.THIN);
        tempStyle.setBorderTop(BorderStyle.THIN);
        tempStyle.setBorderRight(BorderStyle.THICK);
        tempStyle.setBorderLeft(BorderStyle.THICK);
        styles.put("greenStyle", tempStyle);

        tempStyle = workbook.createCellStyle();
        tempStyle.setAlignment(HorizontalAlignment.CENTER);
        tempStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        tempStyle.setFont(whitFont);
        tempStyle.setWrapText(true);
        tempStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tempStyle.setBorderBottom(BorderStyle.THIN);
        tempStyle.setBorderTop(BorderStyle.THIN);
        tempStyle.setBorderRight(BorderStyle.THICK);
        tempStyle.setBorderLeft(BorderStyle.THICK);
        styles.put("redStyle", tempStyle);

        tempStyle = workbook.createCellStyle();
        tempStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        tempStyle.setAlignment(HorizontalAlignment.CENTER);
        tempStyle.setFont(whitFont);
        tempStyle.setWrapText(true);
        tempStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        tempStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tempStyle.setBorderBottom(BorderStyle.THIN);
        tempStyle.setBorderTop(BorderStyle.THIN);
        tempStyle.setBorderRight(BorderStyle.THICK);
        tempStyle.setBorderLeft(BorderStyle.THICK);
        styles.put("titleStyle", tempStyle);

        tempStyle = workbook.createCellStyle();
        tempStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        tempStyle.setAlignment(HorizontalAlignment.CENTER);
        tempStyle.setFont(whitFont);
        tempStyle.setWrapText(true);
        tempStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        tempStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tempStyle.setBorderBottom(BorderStyle.THIN);
        tempStyle.setBorderTop(BorderStyle.THIN);
        tempStyle.setBorderRight(BorderStyle.THICK);
        tempStyle.setBorderLeft(BorderStyle.THICK);
        styles.put("sumStyle", tempStyle);

        tempStyle = workbook.createCellStyle();
        tempStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        tempStyle.setAlignment(HorizontalAlignment.CENTER);
        tempStyle.setFont(blackFont);
        tempStyle.setWrapText(true);
        tempStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        tempStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tempStyle.setBorderBottom(BorderStyle.THIN);
        tempStyle.setBorderTop(BorderStyle.THIN);
        tempStyle.setBorderRight(BorderStyle.THICK);
        tempStyle.setBorderLeft(BorderStyle.THICK);
        styles.put("regularStyle", tempStyle);

        tempStyle = workbook.createCellStyle();
        tempStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        tempStyle.setAlignment(HorizontalAlignment.CENTER);
        tempStyle.setFont(blackFont);
        tempStyle.setWrapText(true);
        tempStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        tempStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tempStyle.setBorderBottom(BorderStyle.THIN);
        tempStyle.setBorderTop(BorderStyle.THIN);
        tempStyle.setBorderRight(BorderStyle.THICK);
        tempStyle.setBorderLeft(BorderStyle.THICK);
        styles.put("yellowStyle", tempStyle);
        return styles;
    }

    //Adding ituff to lists
    public static void addToAll(ItuffObject newItuff) {
        synchronized (BackController.class) {
            if (listItuffs.keySet().contains(newItuff.getFileName())) {
                return;
            }
            if (!newItuff.getStrUlt().equals("No ULT") && listItuffs.values().contains(newItuff)) {
                ItuffObject oldItuff = listItuffs.values().stream()
                        .filter(i -> i.equals(newItuff))
                        .findFirst().get();

                boolean knownDuplicate = false;
                for (WarningObject warning : nonGuiWarnings) {
                    List<ItuffObject> badItuffs = warning.getBadItuffs();
                    if (warning.getWarningType().equals(WarningType.DUPLICATES) && badItuffs.contains(newItuff)) {
                        badItuffs.add(newItuff);
                        Platform.runLater(() -> {
                            Main.controller.removeWarning(warning);
                            Main.controller.addWarning(warning);
                        });
                        knownDuplicate = true;
                        break;
                    }
                }

                if (!knownDuplicate) {
                    WarningObject newWarning = new WarningObject(WarningType.DUPLICATES, oldItuff, newItuff);
                    addWarning(newWarning);
                }
            }

            if (lotNum != null && !newItuff.getLot().equalsIgnoreCase(lotNum)) {
                WarningObject warning = new WarningObject(WarningType.LOT_MISMATCH, newItuff);
                addWarning(warning);
            }
            if (sum != null && !newItuff.getSum().equalsIgnoreCase(sum)) {
                WarningObject warning = new WarningObject(WarningType.SUM_MISMATCH, newItuff);
                addWarning(warning);
            }
            if (newItuff.isGolden()) {
                WarningObject warning = new WarningObject(WarningType.GOLDEN_RESULT, newItuff);
                addWarning(warning);
            }


            //Adding fail bin to graph
            if (!newItuff.getBin().equals("PASS")) {
                Map<String, Map<String, Integer>> binsPerHost = GraphWindow.binsPerHost;
                Map<String, Integer> hostBinCount = binsPerHost.putIfAbsent(newItuff.getBin(), new HashMap<>());
                if (hostBinCount == null) {
                    hostBinCount = binsPerHost.get(newItuff.getBin());
                }
                if (hostBinCount.putIfAbsent(newItuff.getHost(), 1) != null) {
                    hostBinCount.put(newItuff.getHost(), hostBinCount.get(newItuff.getHost()) + 1);
                }
            }

            listItuffs.put(newItuff.getFileName(), newItuff);
            Main.controller.recount();
        }
    }

    //Adding ituff that has different hostname from where it actually is.
    public static void addLostItuff(ItuffObject ituffObj) {
        for (SingleHostProcess process : processes) {
            if (process.getName().equalsIgnoreCase(ituffObj.getHost())) {
                process.addItuff(ituffObj, false);
                return;
            }
        }
    }

    //Removing ituff that has different hostname from where it actually is.
    public static void removeLostItuff(String fileName) {
        for (SingleHostProcess process : processes) {
            if (process.getNotOnHostNames().contains(fileName)) {
                process.removeItuff(fileName);
                return;
            }
        }
    }


    public static void removeItuff(ItuffObject ituff) {
        synchronized (BackController.class) {
            listItuffs.remove(ituff.getFileName());
            if (!ituff.getBin().equals("PASS")) {
                Map<String, Map<String, Integer>> binsPerHost = GraphWindow.binsPerHost;
                Map<String, Integer> hostBinCount = binsPerHost.get(ituff.getBin());
                if (hostBinCount != null) {
                    hostBinCount.replace(ituff.getHost(), hostBinCount.get(ituff.getHost()) - 1);
                }
            }

            int size = nonGuiWarnings.size();
            for (int i = 0; i < size; i++) {
                WarningObject warning = nonGuiWarnings.get(i);
                List<ItuffObject> badItuffs = warning.getBadItuffs();
                if (badItuffs.stream().noneMatch(bi -> bi.getFileName().equals(ituff.getFileName()))) {
                    continue;
                }

                if (!warning.getWarningType().equals(WarningType.DUPLICATES)) {
                    Platform.runLater(() -> Main.controller.removeWarning(warning));
                    nonGuiWarnings.remove(warning);
                    i--;
                    size--;
                } else {
                    if (badItuffs.size() <= 2) {
                        Platform.runLater(() -> Main.controller.removeWarning(warning));
                        nonGuiWarnings.remove(warning);
                        i--;
                        size--;
                    } else {
                        badItuffs.removeIf(it -> it.getFileName().equals(ituff.getFileName()));
                        Platform.runLater(() -> {
                            Main.controller.removeWarning(warning);
                            Main.controller.addWarning(warning);
                        });
                    }
                }
            }
            Main.controller.recount();
        }
    }


    //Mainly formatting merge file here.
    public static void merge(TextArea textArea, String mQty, String mLoc, String mProj, String mEnd) {
        Main.controller.addLog("Merging");
        if (mLoc.equals("") || mQty.equals("") || mProj.equals("")) {
            Platform.runLater(() -> textArea.appendText("Fill all fields!\n"));
            return;
        }
        Platform.runLater(() -> {
            textArea.appendText("Collecting ituffs\n");
            LoadingWindow.show("Collecting");
        });
        StringBuilder monoItuffs = new StringBuilder();
        ArrayList<String> mergedNames = new ArrayList<>(listItuffs.keySet());
        for (SingleHostProcess process : processes) {
            String merge = process.merge(textArea, mLoc, mergedNames);
            if (merge.length() == 0) {
                Platform.runLater(() -> textArea.appendText("Couldn't get ituffs from " + process.getName() + "\n"));
            } else {
                monoItuffs.append(merge);
                Platform.runLater(() -> textArea.appendText("Got ituffs from " + process.getName() + "\n"));
            }
        }
        Platform.runLater(() -> textArea.appendText("Collected\n"));
        Platform.runLater(LoadingWindow::close);
        String[] ituffArr = monoItuffs.toString().split("7_lend");
        if (Integer.parseInt(mQty) != (ituffArr.length - 1)) {
            Platform.runLater(() -> {
                textArea.appendText("Wrong quantity!(" + (ituffArr.length - 1) + ")\n");
                Platform.runLater(LoadingWindow::close);
            });
            return;
        }

        Platform.runLater(() -> LoadingWindow.show("Zipping"));

        String header = monoItuffs.substring(0, monoItuffs.indexOf("3_lsep"));

        StringBuilder finalMerge = new StringBuilder(header);

        for (int i = 0; i < ituffArr.length - 1; i++) {
            ituffArr[i] = ituffArr[i].replaceAll("3_prtnm_[\\d]+", "3_prtnm_" + (i + 1));
            finalMerge.append(ituffArr[i], ituffArr[i].indexOf("3_lsep"), ituffArr[i].indexOf("3_lend"));
        }
        String lastItuff = ituffArr[ituffArr.length - 2] + "7_lend";
        lastItuff = lastItuff.replaceAll("4_total_[\\d]+", "4_total_" + (ituffArr.length - 1));
        finalMerge.append(lastItuff, lastItuff.indexOf("3_lend"), lastItuff.length());

        File zip = new File(Main.auth.getProperty("merge_path") + lotNum + "_" + mLoc + "_" + sum + "_" + "ALL.zip");

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            ZipEntry e = new ZipEntry(lotNum + mEnd + "_" + mLoc + "_" + sum + "_" + "ALL");
            out.putNextEntry(e);
            byte[] data = finalMerge.toString().getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();
        } catch (IOException e) {
            Platform.runLater(() -> {
                textArea.appendText("Couldn't zip ituffs due to" + e.getMessage() + "\n");
                LoadingWindow.close();
            });
            return;
        }
        Platform.runLater(() -> {
            textArea.appendText("Ziped ituffs successfully\n");
            LoadingWindow.close();
        });
        Platform.runLater(() -> LoadingWindow.show("Uploading"));

        if (!uploadToMainPC(finalMerge.toString(), mProj, textArea)) {
            Platform.runLater(LoadingWindow::close);
            return;
        }
        Platform.runLater(() -> textArea.appendText("Uploaded to Main PC successfully\n"));
        if (!uploadToMidas(zip, textArea)) {
            Platform.runLater(LoadingWindow::close);
            return;
        }
        Platform.runLater(() -> {
            textArea.appendText("Uploaded to Midas successfully\n");
            LoadingWindow.close();
            LoadingWindow.show("Deleting");
        });

        for (File file : Objects.requireNonNull(new File(Main.auth.getProperty("backUpFolder") + lotNum + "/" + sum).listFiles())) {//fix backUp
            if (!listItuffs.keySet().contains(file.getName())) {
                file.delete();
            }
        }

        for (SingleHostProcess process : processes) {//remove ituffs
            process.createItuffsJson();
            process.removeAll(listItuffs.keySet());
        }
        Platform.runLater(() -> {
            Platform.runLater(LoadingWindow::close);
            Main.controller.stopProcesses();
        });
    }

    @SneakyThrows
    private static boolean uploadToMainPC(String finalMerge, String projName, TextArea textArea) {
        JSch jsch;
        Session session = null;
        ChannelSftp sftpChannel = null;
        boolean res;
        try (InputStream mergeIn = IOUtils.toInputStream(finalMerge)) {// connection
            jsch = new JSch();
            session = jsch.getSession(Main.auth.getProperty("user"), Main.auth.getProperty("main_pc_host"));
            session.setPassword(Main.auth.getProperty("password"));
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            String path = projName + "/Data_log/" + lotNum + "/";
            String[] folders = path.split("/");
            sftpChannel.cd(Main.auth.getProperty("main_merge_path"));
            for (String folder : folders) {
                if (folder.length() > 0) {
                    try {
                        try {
                            sftpChannel.chmod(Integer.parseInt("777", 8), folder);
                        } catch (Exception e) {
                            Main.controller.addExceptionLog(e, Thread.currentThread().getName());
                        }
                        sftpChannel.cd(folder);
                        Platform.runLater(() -> Main.controller.addLog(folder));
                    } catch (SftpException e) {
                        sftpChannel.mkdir(folder);
                        sftpChannel.chmod(Integer.parseInt("777", 8), folder);
                        sftpChannel.cd(folder);
                    }
                }
            }

            sftpChannel.put(mergeIn, Main.auth.getProperty("main_merge_path") + path + sum);
            //sftpChannel.chmod(Integer.parseInt("777", 8), Main.auth.getProperty("main_merge_path") + path + sum);
            res = true;
        } catch (Exception e) {
            Platform.runLater(() -> {
                textArea.appendText("Failed to upload due to " + e.getMessage());
                Main.controller.addExceptionLog(e, Thread.currentThread().getName());
            });
            res = false;
        } finally {
            if (sftpChannel != null && !sftpChannel.isClosed()) {
                sftpChannel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        return res;
    }

    private static boolean uploadToMidas(File zip, TextArea textArea) {
        JSch jsch;
        Session session = null;
        ChannelSftp sftpChannel = null;
        boolean res = true;

        try {// connection
            jsch = new JSch();
            session = jsch.getSession(Main.auth.getProperty("midas_user"), Main.auth.getProperty("midas_host"));
            session.setPassword(Main.auth.getProperty("midas_password"));
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            File sig = new File(zip.getParent() + "/" + zip.getName().replace("zip", "sig"));
            sig.createNewFile();

            sftpChannel.put(zip.getPath(), Main.auth.getProperty("midas_zip_path"));
            textArea.appendText("Zip uploaded\n");
            sftpChannel.put(sig.getPath(), Main.auth.getProperty("midas_sig_path"));
            textArea.appendText("Signal uploaded\n");

            sig.delete();
            res = true;
        } catch (JSchException | SftpException e) {
            textArea.appendText("Failed to upload due to " + e.getMessage());
            Platform.runLater(() -> Main.controller.addExceptionLog(e, Thread.currentThread().getName()));
            res = false;
        } catch (IOException e) {
            textArea.appendText("Can't create file " + e.getMessage());
            Platform.runLater(() -> Main.controller.addExceptionLog(e, Thread.currentThread().getName()));
        } finally {
            if (sftpChannel != null && !sftpChannel.isClosed()) {
                sftpChannel.exit();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        return res;
    }


}



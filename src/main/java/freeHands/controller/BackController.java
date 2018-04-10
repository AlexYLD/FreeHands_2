package freeHands.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import freeHands.entity.CommentObj;
import freeHands.entity.ItuffObject;
import freeHands.gui.GraphWindow;
import freeHands.gui.Main;
import freeHands.model.SingleHostProcess;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCreationHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class BackController {
    public static Map<String, SortedList<ItuffObject>> ituffs = new HashMap<>();
    static Map<String, ItuffObject> listItuffs = new HashMap<>();
    static List<String> warnings = FXCollections.observableArrayList();
    private static Set<SingleHostProcess> processes = new HashSet<>();

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

    static void connect(String host, LocalDateTime fromDateTime, LocalDateTime toDateTime) {
        SingleHostProcess process = new SingleHostProcess(host, fromDateTime, toDateTime);
        addProcess(process);
    }

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
                path = Main.auth.getProperty("commentsFolder") + host.toLowerCase();
                new File(path).mkdirs();
                mapper.writeValue(new File(path + "/comment" + commentObj.getHost() + System.currentTimeMillis()), commentObj);
                return;
            }
        }
    }

    @SneakyThrows
    public static void stopProcesses() {
        for (SingleHostProcess process : processes) {
            process.interrupt();
        }

        ituffs.clear();
        listItuffs.clear();
        warnings.clear();
        processes.clear();
        FileUtils.write(new File(Main.auth.getProperty("authorized_keys")), "", false);
    }

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
            sheetName = sheetName + "-another";
        }
        sheetBins = workbook.createSheet(sheetName);


        Map<String, CellStyle> styles = getStyles(workbook);


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

        Set<String> binsSet = listItuffs.values().stream()
                .map(ItuffObject::getBin)
                .filter(b -> !b.equals("PASS"))
                .collect(Collectors.toSet());
        List<String> bins = new ArrayList<>(binsSet);

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

    public synchronized static void addToAll(ItuffObject ituffObj) {
        int errCount = warnings.size();
        if (listItuffs.keySet().contains(ituffObj.getFileName())) {
            return;
        }
        if (!ituffObj.getStrUlt().equals("No ULT") && listItuffs.values().contains(ituffObj)) {
            List<ItuffObject> list = new ArrayList<>(listItuffs.values());
            ItuffObject ituff = list.get(list.indexOf(ituffObj));
            warnings.add("Duplicates: " + ituff.getFileName() + " on " + ituff.getHost() + " and " + ituffObj.getFileName() + " on " + ituffObj.getHost());
        }

        if (lotNum != null && !ituffObj.getLot().equalsIgnoreCase(lotNum)) {
            warnings.add("Lot mismatch in " + ituffObj.getFileName() + " on " + ituffObj.getHost());
        }
        if (sum != null && !ituffObj.getSum().equalsIgnoreCase(sum)) {
            warnings.add("Sum mismatch in " + ituffObj.getFileName() + " on " + ituffObj.getHost());
        }
        if (!Main.controller.getWarningButton().getStyleClass().contains("warning-red") && !warnings.isEmpty()) {
            Main.controller.getWarningButton().getStyleClass().removeAll("warning-red", "warning-green");
            Main.controller.getWarningButton().getStyleClass().add("warning-red");
        }

        if (Main.controller.prodModeV.isSelected() && errCount != warnings.size()) {
            Main.controller.showWarnings();
        }

        if (!ituffObj.getBin().equals("PASS")) {
            Map<String, Map<String, Integer>> binsPerHost = GraphWindow.binsPerHost;
            Map<String, Integer> hostBinCount = binsPerHost.putIfAbsent(ituffObj.getBin(), new HashMap<>());
            if (hostBinCount == null) {
                hostBinCount = binsPerHost.get(ituffObj.getBin());
            }
            if (hostBinCount.putIfAbsent(ituffObj.getHost(), 1) != null) {
                hostBinCount.put(ituffObj.getHost(), hostBinCount.get(ituffObj.getHost()) + 1);
            }
        }

        listItuffs.put(ituffObj.getFileName(), ituffObj);
        Main.controller.recount();

    }

    public static void addLostItuff(ItuffObject ituffObj) {
        for (SingleHostProcess process : processes) {
            if (process.getName().equalsIgnoreCase(ituffObj.getHost())) {
                process.addItuff(ituffObj, false);
                return;
            }
        }
    }

    public static void removeLostItuff(String fileName) {
        for (SingleHostProcess process : processes) {
            if (process.getNotOnHostNames().contains(fileName)) {
                process.removeItuff(fileName);
                return;
            }
        }
    }


    public static synchronized void removeItuff(String fileName) {
        listItuffs.remove(fileName);
        Main.controller.recount();
    }


    public synchronized static void addSSHKey(String key) throws IOException {
        FileUtils.write(new File(Main.auth.getProperty("authorized_keys")), key + "\n", true);
    }
}

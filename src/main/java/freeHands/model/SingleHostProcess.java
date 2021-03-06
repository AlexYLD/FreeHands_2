//Created by Alexey Yarygin
package freeHands.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.*;
import freeHands.controller.BackController;
import freeHands.entity.CommentObj;
import freeHands.entity.ItuffObject;
import freeHands.entity.WarningObject;
import freeHands.entity.WarningType;
import freeHands.gui.Main;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TextArea;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
public class SingleHostProcess extends Thread {
    private String host;
    private ItuffObject from = new ItuffObject();
    private ItuffObject to = new ItuffObject();
    private JSch jsch;
    private Session session;
    private ChannelSftp sftpChannel;
    private ChannelExec execChannel;
    ObservableList<ItuffObject> ituffs;
    private List<String> existNames;//ituffs on this host
    private List<String> notOnHostNames;//ituffs from this host that somehow are now on another.
    private boolean myInterrupt;//Because I don't like regular interrupt.
    private String signature;

    //Prevents double connection.
    private final Object connectionLock = new Object();


    public SingleHostProcess(String host) {
        //Using hostname or IP.
        if (host.contains(" ")) {
            this.host = host.substring(host.indexOf(" ") + 1);
            this.setName(host.substring(0, host.indexOf(" ")));
        } else {
            this.host = host + ".iil.intel.com";
            this.setName(host);
        }

        //Signature of this pc in script name so few apps can watch over one machine.
        try {
            signature = "_" + InetAddress.getLocalHost().getHostName() + "_" + String.valueOf(System.currentTimeMillis());
        } catch (UnknownHostException e) {
            signature = "_" + "MyPc" + String.valueOf(System.currentTimeMillis());
        }

        myInterrupt = false;
        from.setDate(new Date(0));
        to.setDate(new Date(Long.parseLong("9999999999999")));
        existNames = new ArrayList<>();
        notOnHostNames = new ArrayList<>();
        ituffs = FXCollections.observableArrayList();
    }

    //Same as previous constructor except for date.
    public SingleHostProcess(String host, LocalDateTime fromDateTime, LocalDateTime toDateTime) {
        if (host.contains(" ")) {
            this.host = host.substring(host.indexOf(" ") + 1);
            this.setName(host.substring(0, host.indexOf(" ")));
        } else {
            this.host = host + ".iil.intel.com";
            this.setName(host);
        }

        try {
            signature = "_" + InetAddress.getLocalHost().getHostName() + "_" + String.valueOf(System.currentTimeMillis());
        } catch (UnknownHostException e) {
            signature = "_" + "MyPc" + String.valueOf(System.currentTimeMillis());
        }

        myInterrupt = false;
        from.setDate(new Date(fromDateTime.atZone(ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli()));
        to.setDate(new Date(toDateTime.atZone(ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli()));
        existNames = new ArrayList<>();
        notOnHostNames = new ArrayList<>();
        ituffs = FXCollections.observableArrayList();
    }

    @Override
    public void run() {
        //Notifications folder
        File flagsFolder = new File(Main.auth.getProperty("flagsFolder"));

        firstConnect();
        Main.controller.setHostStatus(this.getName(), true);
        if (myInterrupt) {
            return;
        }
        getComments();
        checkAndTake();//getting new ituffs

        List<File> list;
        File file;
        boolean found;

        while (!myInterrupt) {
            //Listening
            found = false;
            list = new ArrayList(Arrays.asList(flagsFolder.listFiles()));
            for (int i = 0; i < list.size(); i++) {
                file = list.get(i);
                if (!found && file.getName().toLowerCase().startsWith(this.getName().toLowerCase())) {
                    checkAndTake();
                    found = true;
                }
                if (found && file.getName().toLowerCase().startsWith(this.getName().toLowerCase())) {
                    list.remove(i);
                    file.delete();
                    i--;
                }
                if (file.getName().toLowerCase().startsWith("json_" + this.getName().toLowerCase())) {
                    createItuffsJson();
                    list.remove(i);
                    file.delete();
                    i--;
                }
            }
            if (!myInterrupt) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    currentThread().interrupt();
                    myInterrupt();
                    break;
                }
            }
        }

        //Closing and disconnecting
        if (!connectToHost()) {
            return;
        }
        try {
            execChannel = (ChannelExec) session.openChannel("exec");
            execChannel.setCommand("rm " + Main.auth.getProperty("remoteListenerLocation") + signature + ".sh");
            execChannel.connect();
            Platform.runLater(() -> Main.controller.addLog(this.getName() + " Tracker stopped"));
            while (!execChannel.isClosed()) {
                Thread.sleep(500);
            }
            disconnect();
        } catch (JSchException | InterruptedException e) {
            currentThread().interrupt();
            myInterrupt();
            Platform.runLater(() -> Main.controller.addExceptionLog(e, Thread.currentThread().getName()));
        }

        Platform.runLater(() -> Main.controller.addLog(this.getName() + " Out"));
    }

    public void createItuffsJson() {
        File json = new File(Main.auth.getProperty("jsonBackUpFolder") + this.getName() + "_" + System.currentTimeMillis() + ".json");
        StringBuilder content = new StringBuilder("[\n");
        for (ItuffObject ituff : ituffs) {
            if (ituff.getFileName().startsWith("comment")) {
                continue;
            }
            content.append("{\"fileName\":\"").append(ituff.getFileName()).append("\",\n");
            content.append("\"bin\":\"").append(ituff.getBin()).append("\",\n");
            content.append("\"lot\":\"").append(ituff.getLot()).append("\",\n");
            content.append("\"sum\":\"").append(ituff.getSum()).append("\",\n");
            content.append("\"host\":\"").append(ituff.getHost()).append("\",\n");
            content.append("\"loc\":\"").append(ituff.getLocation()).append("\",\n");
            content.append("\"date\":\"").append(ituff.getDate().getTime()).append("\"},\n");
        }
        content.append("]");
        int lastIndexOf = content.lastIndexOf(",");
        if (lastIndexOf > 0) {
            content.replace(lastIndexOf, lastIndexOf + 1, "");
        }
        try {
            FileUtils.writeStringToFile(json, content.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Platform.runLater(() -> {
                Main.controller.addLog("Can't write file due to: " + e.getCause() + " " + e.getMessage());
                Main.controller.addExceptionLog(e, Thread.currentThread().getName());
            });
        }
    }


    public void myInterrupt() {
        myInterrupt = true;
    }

    private void checkAndTake() {
        synchronized (connectionLock) {
            List<String> remoteNames;
            Vector<ChannelSftp.LsEntry> remoteFolderVector = getRemoteNamesVector();

            if (remoteFolderVector != null) {
                remoteNames = remoteFolderVector.stream()
                        .map(ChannelSftp.LsEntry::getFilename)
                        .collect(Collectors.toList());//getting file names from linux
                for (String fileName : remoteNames) {
                    if (!fileName.startsWith("Dut") || fileName.endsWith("~") || existNames.contains(fileName)) {
                        continue;
                    }
                    StringBuilder ituffText = getItuffText(fileName);
                    if (ituffText == null) break;

                    ItuffObject ituffObj = new ItuffObject(fileName, ituffText.toString());
                    if (ituffObj.getHost().equals("not found")) {
                        ituffObj.setHost(this.getName());
                    }
                    if (ituffText.length() == 0) {
                        BackController.addWarning(new WarningObject(WarningType.EMPTY_FILE, ituffObj));

                        existNames.add(fileName);
                    } else {
                        if (!ituffText.toString().matches("[\\s\\S]*7_lend[\\s]*")) {
                            BackController.addWarning(new WarningObject(WarningType.DAMAGED_FILE, ituffObj));
                        }
                        //Back up ituff
                        String path = Main.auth.getProperty("backUpFolder") + ituffObj.getLot() + "/" + ituffObj.getSum() + "/";
                        File backUpFolder = new File(path);
                        boolean mkdirs = true;
                        if (!backUpFolder.exists()) {
                            mkdirs = backUpFolder.mkdirs();
                        }
                        if (mkdirs && backUpFolder.list((file1, s) -> s.equals(fileName)).length == 0) {
                            try {
                                FileUtils.writeStringToFile(new File(path + fileName), ituffText.toString(), "UTF-8");
                            } catch (IOException e) {
                                Platform.runLater(() -> Main.controller.addExceptionLog(e, Thread.currentThread().getName()));
                            }
                        }
                        addItuff(ituffObj, true);
                    }

                }
                //Checking if some ituffs were removed
                for (int i = 0; i < existNames.size(); i++) {
                    String fileName = existNames.get(i);
                    if (!remoteNames.contains(fileName)) {
                        removeItuff(fileName);
                        i--;
                    }
                }
            }

            disconnect();
            Platform.runLater(() -> Main.controller.addLog(this.getName() + " Got ituffs"));
        }
    }

    //check that connectToHost() was called before and sftp is in the right folder.
    private StringBuilder getItuffText(String fileName) {
        StringBuilder ituffText = new StringBuilder();
        try (InputStream is = sftpChannel.get(fileName); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                ituffText.append(line).append("\n");
            }
        } catch (IOException | SftpException e) {
            return null;
        }
        return ituffText;
    }

    private Vector<ChannelSftp.LsEntry> getRemoteNamesVector() {
        Vector<ChannelSftp.LsEntry> remoteFolderVector = null;
        while (!myInterrupt) {
            waitTillAvailable();
            if (myInterrupt) {
                break;
            }

            if (connectToHost()) {
                try {
                    sftpChannel.cd(Main.auth.getProperty("remoteFolder"));
                    remoteFolderVector = sftpChannel.ls(Main.auth.getProperty("remoteFolder"));
                    break;
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        Main.controller.addLog(this.getName() + " Failed to get ituffs");
                        if (!e.getMessage().contains("No such file")) {
                            Main.controller.addExceptionLog(e, Thread.currentThread().getName());
                        }
                    });

                    disconnect();
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    currentThread().interrupt();
                    myInterrupt();
                    break;
                }
            }
        }
        return remoteFolderVector;
    }

    //Puts listener bash script in linux machine and executes it.
    @SneakyThrows
    private void firstConnect() {
        StringWriter writer = new StringWriter();
        IOUtils.copy(Main.class.getResourceAsStream("/ituff_tracker"), writer);
        String listenerCode = writer.toString();
        String remoteListenerLocation = Main.auth.getProperty("remoteListenerLocation") + signature + ".sh";

        listenerCode = listenerCode.replaceAll("tracker", "tracker" + signature + ".sh");
        listenerCode = listenerCode.replace("IP", InetAddress.getLocalHost().getHostAddress());
        listenerCode = listenerCode.replace("port", Main.auth.getProperty("server_port"));
        while (true) {
            waitTillAvailable();
            if (myInterrupt) {
                return;
            }
            if (connectToHost()) {
                try {
                    String oldTrackerLoc = remoteListenerLocation.substring(0, remoteListenerLocation.lastIndexOf("/"));
                    execChannel = (ChannelExec) session.openChannel("exec");
                    String command = "rm $(find " + oldTrackerLoc + " | grep ituff_tracker" + signature.substring(0, signature.lastIndexOf("_")) + ");" +
                            "echo '" + listenerCode + "' > " + remoteListenerLocation + ";" +
                            "chmod +x " + remoteListenerLocation + ";" +
                            "nohup " + remoteListenerLocation + " &>/dev/null";
                    // "nohup "+ remoteListenerLocation + " &>" +Main.auth.getProperty("remoteListenerLocation")+"log";
                    execChannel.setCommand(command);
                    execChannel.connect();
                    disconnect();
                    break;
                } catch (Exception e) {
                    Platform.runLater(() -> Main.controller.addExceptionLog(e, Thread.currentThread().getName()));
                    disconnect();
                    sleep(10000);
                }
            }
        }

    }

    public synchronized void removeItuff(String fileName) {
        //Platform.runLater is JavaFx's safe way of changing GUI dinamicly
        final Object lock = new Object();

        ItuffObject ituff = ituffs.stream()
                .filter(i -> i.getFileName().equals(fileName))
                .findFirst()
                .orElse(null);

        if (ituff != null) {
            Platform.runLater(() -> {
                synchronized (lock) {
                    ituffs.removeIf(i -> i.getFileName().equals(fileName));
                    lock.notify();
                }
            });
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Main.controller.addExceptionLog(e, Thread.currentThread().getName());
                }
            }
            BackController.removeItuff(ituff);
        } else {
            BackController.removeLostItuff(fileName);
        }

        existNames.remove(fileName);
        notOnHostNames.remove(fileName);
    }


    public synchronized void addItuff(ItuffObject ituffObj, boolean isOnHost) {
        if (ituffObj.compareTo(from) < 0 || ituffObj.compareTo(to) > 0) {
            return;
        }

        if ((!isOnHost && existNames.contains(ituffObj.getFileName())) || notOnHostNames.contains(ituffObj.getFileName())) {
            return;
        }

        if (!isOnHost) {
            notOnHostNames.add(ituffObj.getFileName());
        } else {
            existNames.add(ituffObj.getFileName());
        }

        if (!ituffObj.getHost().equalsIgnoreCase(this.getName())) {
            BackController.addLostItuff(ituffObj);
            return;
        }

        Platform.runLater(() -> ituffs.add(ituffObj));

        if (ituffObj.getFileName().startsWith("comment")) {
            return;
        }

        //Platform.runLater(() -> BackController.addToAll(ituffObj));
        BackController.addToAll(ituffObj);

    }

    //Pinging host
    private void waitTillAvailable() {
        boolean slept = false;
        while (!myInterrupt) {
            try {
                if (InetAddress.getByName(host).isReachable(2000)) {
                    if (slept) {
                        Main.controller.setHostStatus(this.getName(), true);
                    }
                    break;
                } else {
                    throw new IOException("");
                }
            } catch (IOException e) {
                Platform.runLater(() -> Main.controller.addLog(this.getName() + " Unreacheble"));
            }
            try {
                if (!slept) {
                    Main.controller.setHostStatus(this.getName(), false);
                    slept = true;
                }

                sleep(10000);
            } catch (InterruptedException ex) {
                currentThread().interrupt();
                myInterrupt();
                return;
            }
        }
    }

    /*
     * Every method that uses this one should be synchronized by connectionLock
     * and end with calling of disconnect()
     * */
    private boolean connectToHost() {
        try {// connection
            jsch = new JSch();
            session = jsch.getSession(Main.auth.getProperty("user"), host);
            session.setPassword(Main.auth.getProperty("password"));
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            return true;// connected
        } catch (JSchException e) {
            Platform.runLater(() -> Main.controller.addExceptionLog(e, Thread.currentThread().getName()));
            disconnect();
            return false;
        }
    }

    private void disconnect() {
        if (sftpChannel != null && !sftpChannel.isClosed()) {
            sftpChannel.exit();
        }
        if (execChannel != null && !execChannel.isClosed()) {
            execChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    //Runs when loads
    private void getComments() {
        ObjectMapper mapper = new ObjectMapper();
        File commentFolder = new File(Main.auth.getProperty("commentsFolder") + this.getName().toLowerCase());
        if (commentFolder.exists()) {
            for (File file : commentFolder.listFiles()) {
                try {
                    addItuff(mapper.readValue(file, CommentObj.class), false);
                } catch (IOException e) {
                    Platform.runLater(() -> Main.controller.addExceptionLog(e, Thread.currentThread().getName()));
                }
            }
        }
    }

    //Compressing all ituffs to one. Also checking some parameters.
    public String merge(TextArea textArea, String mLoc, ArrayList<String> mergedNames) {
        synchronized (connectionLock) {
            StringBuilder mergedItuffs = new StringBuilder();
            if (connectToHost()) {
                for (String fileName : existNames) {
                    //String fileName = ituffObject.getFileName();
                    if (fileName.toLowerCase().startsWith("comment") || !mergedNames.contains(fileName)) {
                        continue;
                    }
                    mergedNames.remove(fileName);
                    StringBuilder ituffText = new StringBuilder();
                    fileName = Main.auth.getProperty("remoteFolder") + fileName;
                    try (InputStream is = sftpChannel.get(fileName); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            ituffText.append(line).append("\n");
                        }
                    } catch (IOException | SftpException e) {
                        break;
                    }
                    if (!ituffText.toString().matches("[\\s\\S]*7_lend[\\s]*")) {
                        textArea.appendText(fileName + " on " + this.getName() + " is damaged\n");
                        continue;
                    }
                    ItuffObject tempItuffObject = new ItuffObject(fileName.substring(fileName.lastIndexOf("/") + 1), ituffText.toString());
                    String testName = "2_tname_network_ok";
                    int startIndex = ituffText.indexOf(testName);
                    if (startIndex >= 0) {
                        ituffText.replace(startIndex, startIndex + testName.length(),
                                "2_tname_MachineName\n2_mrslt_" + tempItuffObject.getHost().replaceAll("[\\D]*", "") + "\n" + testName);
                    }
                    testName = "3_curfbin_";
                    String bin = tempItuffObject.getBin().replace(".", "");
                    if (bin.equals("PASS")) {
                        bin = "1100";
                    }
                    startIndex = ituffText.indexOf(testName);
                    if (startIndex >= 0) {
                        ituffText.replace(startIndex, ituffText.indexOf("\n", startIndex),
                                testName + bin);
                    }
                    if (!tempItuffObject.getLocation().equals(mLoc)) {
                        textArea.appendText("Different location in " + tempItuffObject.getFileName() + " on " + tempItuffObject.getHost() + "\n");
                    }
                    mergedItuffs.append(ituffText);
                }
            }

            disconnect();
            return mergedItuffs.toString();
        }
    }

    //Removing ituffs after merge.
    public boolean removeAll(Set<String> filenames) {
        synchronized (connectionLock) {
            if (!connectToHost()) {
                return false;
            }
            boolean wasException = false;
            String fullFileName;
            for (String fileName : filenames) {
                if (fileName.toLowerCase().startsWith("comment")) {
                    continue;
                }
                fullFileName = Main.auth.getProperty("remoteFolder") + fileName;
                try {
                    if (existNames.contains(fileName)) {
                        sftpChannel.rm(fullFileName);
                    }
                } catch (SftpException e) {
                    wasException = true;
                    Platform.runLater(() -> {
                        Main.controller.addLog("Cant remove because " + e.getMessage() + " " + e.getCause());
                        Main.controller.addExceptionLog(e, Thread.currentThread().getName());
                    });
                }
            }
            disconnect();
            return !wasException;
        }

    }

    public String getSingleItuffText(String fullFileName) {
        synchronized (connectionLock) {
            if (!connectToHost()) {
                return null;
            }
            StringBuilder ituffText = getItuffText(fullFileName);
            disconnect();
            return ituffText != null ? ituffText.toString() : "";
        }
    }

    public boolean setItuffText(String fullFileName, String newText) {
        synchronized (connectionLock) {
            if (!connectToHost()) {
                return false;
            }
            boolean res;
            try {
                sftpChannel.rm(fullFileName);
                sftpChannel.put(new ByteArrayInputStream(newText.getBytes(StandardCharsets.UTF_8)), fullFileName);
                res = true;
            } catch (SftpException e) {
                res = false;
                Platform.runLater(() -> Main.controller.addExceptionLog(e, Thread.currentThread().getName()));
            }
            disconnect();
            return res;
        }
    }

}



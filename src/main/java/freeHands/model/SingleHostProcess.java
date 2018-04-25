package freeHands.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.*;
import freeHands.controller.BackController;
import freeHands.entity.CommentObj;
import freeHands.entity.ItuffObject;
import freeHands.gui.Main;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TextArea;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
    private List<String> existNames;
    private List<String> notOnHostNames;
    private boolean myInterrupt;
    private String signature;

    private final Object mergeLock = new Object();


    public SingleHostProcess(String host) {
        if (host.contains(" ")) {
            this.host = host.substring(host.indexOf(" ") + 1);
            this.setName(host.substring(0, host.indexOf(" ")));
        } else {
            this.host = host + ".iil.intel.com";
            this.setName(host);
        }
        try {
            signature = "_" + InetAddress.getLocalHost().getHostName() + String.valueOf(System.currentTimeMillis());
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

    public SingleHostProcess(String host, LocalDateTime fromDateTime, LocalDateTime toDateTime) {
        if (host.contains(" ")) {
            this.host = host.substring(host.indexOf(" "));
            this.setName(host.substring(0, host.indexOf(" ")));
        } else {
            this.host = host + ".iil.intel.com";
            this.setName(host);
        }
        signature = "_" + this.getName() + String.valueOf(System.currentTimeMillis());
        myInterrupt = false;
        from.setDate(new Date(fromDateTime.atZone(ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli()));
        to.setDate(new Date(toDateTime.atZone(ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli()));
        existNames = new ArrayList<>();
        notOnHostNames = new ArrayList<>();
        ituffs = FXCollections.observableArrayList();
    }

    @Override
    public void run() {
        File flagsFolder = new File(Main.auth.getProperty("flagsFolder"));

        firstConnect();
        if (myInterrupt) {
            return;
        }
        getComments();
        checkAndTake();


        while (!myInterrupt) {
            List<File> list = new ArrayList(Arrays.asList(flagsFolder.listFiles()));
            for (int i = 0; i < list.size(); i++) {
                File file = list.get(i);
                if (file.getName().toLowerCase().startsWith(this.getName().toLowerCase())) {
                    checkAndTake();
                    list.remove(file);
                    file.delete();
                    break;
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


        if (!connectToHost()) {
            return;
        }
        try {
            execChannel = (ChannelExec) session.openChannel("exec");
            execChannel.setCommand("rm " + Main.auth.getProperty("switchLocation") + signature + ";"
                    + "rm " + Main.auth.getProperty("remoteListenerLocation") + signature + ".sh");
            execChannel.connect();
            System.out.println(this.getName() + " Tracker stopped");
            while (!execChannel.isClosed()) {
                Thread.sleep(500);
            }
            disconnect();
        } catch (JSchException | InterruptedException e) {
            currentThread().interrupt();
            myInterrupt();
            e.printStackTrace();
        }

        System.out.println(this.getName() + " Out");
    }


    public void myInterrupt() {
        myInterrupt = true;
    }

    private void checkAndTake() {
        synchronized (mergeLock) {
            List<String> remoteNames;
            Vector<ChannelSftp.LsEntry> remoteFolderVector = getRemoteNamesVector();

            if (remoteFolderVector != null) {
                remoteNames = remoteFolderVector.stream()
                        .map(ChannelSftp.LsEntry::getFilename)
                        .collect(Collectors.toList());
                for (String fileName : remoteNames) {
                    if (!fileName.startsWith("Dut") || fileName.endsWith("~") || existNames.contains(fileName)) {
                        continue;
                    }
                    StringBuilder ituffText = new StringBuilder();
                    try (InputStream is = sftpChannel.get(fileName); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            ituffText.append(line).append("\n");
                        }
                    } catch (IOException | SftpException e) {
                        break;
                    }
                    if (ituffText.length() > 0) {
                        ItuffObject ituffObj = new ItuffObject(fileName, ituffText.toString());
                        String path = "C:/FreeHandsStuff/BackUp/" + ituffObj.getLot() + "/" + ituffObj.getSum() + "/";
                        File backUpFolder = new File(path);
                        boolean mkdirs = true;
                        if (!backUpFolder.exists()) {
                            mkdirs = backUpFolder.mkdirs();
                        }
                        if (mkdirs && backUpFolder.list((file1, s) -> s.equals(fileName)).length == 0) {
                            try {
                                FileUtils.writeStringToFile(new File(path + fileName), ituffText.toString(), "UTF-8");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        addItuff(ituffObj, true);
                    }

                }
                for (int i = 0; i < existNames.size(); i++) {
                    String fileName = existNames.get(i);
                    if (!remoteNames.contains(fileName)) {
                        removeItuff(fileName);
                        i--;
                    }
                }
            }

            disconnect();
            System.out.println(this.getName() + " Got ituffs");
        }
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
                    System.out.println(this.getName() + " Failed to get ituffs");
                    disconnect();
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    currentThread().interrupt();
                    myInterrupt();
                    break;
                }
            }
        }
        return remoteFolderVector;
    }

    @SneakyThrows
    private void firstConnect() {
        String listenerCode = FileUtils.readFileToString(new File(Main.class.getClassLoader().getResource("ituff_tracker").getFile()));
        //String signedListenerLocation = Main.auth.getProperty("listenerLocation") + signature + ".sh";
        String remoteListenerLocation = Main.auth.getProperty("remoteListenerLocation") + signature + ".sh";

        listenerCode = listenerCode.replaceFirst("switch", "switch" + signature).replaceFirst("tracker", "tracker" + signature);
        listenerCode = listenerCode.replace("ssh ", "ssh " + Main.auth.getProperty("ssh_name"));
        //File signedListener = new File(signedListenerLocation);
        while (true) {
            waitTillAvailable();
            if (myInterrupt) {
                return;
            }
            if (connectToHost()) {
                try (InputStream is = sftpChannel.get(Main.auth.getProperty("keyLocation")); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    StringBuilder keyStr = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        keyStr.append(line);
                    }
                    BackController.addSSHKey(keyStr.toString());

                    execChannel = (ChannelExec) session.openChannel("exec");
                    String command = "echo '" + listenerCode + "' > " + remoteListenerLocation + ";" +
                            "echo \"true\" > " + Main.auth.getProperty("switchLocation") + signature + ";" +
                            "chmod +x " + remoteListenerLocation + ";" +
                            "nohup " + remoteListenerLocation + " &>/dev/null";
                    execChannel.setCommand(command);
                    execChannel.connect();
                    disconnect();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public synchronized void removeItuff(String fileName) {
        Platform.runLater(() -> {
            Iterator<ItuffObject> iterator = ituffs.iterator();
            while (iterator.hasNext()) {
                ItuffObject ituff = iterator.next();
                if (ituff.getFileName().equals(fileName)) {
                    iterator.remove();
                    BackController.removeItuff(fileName);
                    return;
                }
            }
            BackController.removeLostItuff(fileName);
        });
        existNames.remove(fileName);
    }

    public synchronized void addItuff(ItuffObject ituffObj, boolean isOnHost) {
        if (ituffObj.compareTo(from) > 0 && ituffObj.compareTo(to) < 0) {
            if (existNames.contains(ituffObj.getFileName()) || notOnHostNames.contains(ituffObj.getFileName())) {
                return;
            }

            if (!ituffObj.getHost().equalsIgnoreCase(this.getName())) {
                BackController.addLostItuff(ituffObj);
                existNames.add(ituffObj.getFileName());
                return;
            }

            Platform.runLater(() -> ituffs.add(ituffObj));

            if (!ituffObj.getFileName().startsWith("comment")) {
                Platform.runLater(() -> BackController.addToAll(ituffObj));
                if (isOnHost) {
                    existNames.add(ituffObj.getFileName());
                } else {
                    notOnHostNames.add(ituffObj.getFileName());
                }
            }
        }
    }

    private void waitTillAvailable() {
        while (!myInterrupt) {
            try {
                if (InetAddress.getByName(host).isReachable(2000)) {
                    break;
                } else {
                    throw new IOException("");
                }
            } catch (IOException e) {
                System.err.println(this.getName() + " Unreacheble");
            }
            try {
                sleep(10000);
            } catch (InterruptedException ex) {
                currentThread().interrupt();
                myInterrupt();
                return;
            }
        }
    }

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
            e.printStackTrace();
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

    private void getComments() {
        ObjectMapper mapper = new ObjectMapper();
        File commentFolder = new File(Main.auth.getProperty("commentsFolder") + this.getName().toLowerCase());
        if (commentFolder.exists()) {
            for (File file : commentFolder.listFiles()) {
                try {
                    addItuff(mapper.readValue(file, CommentObj.class), false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String merge(TextArea textArea, String mLoc) {
        synchronized (mergeLock) {
            StringBuilder mergedItuffs = new StringBuilder();
            if (connectToHost()) {
                for (ItuffObject ituffObject : ituffs) {
                    String fileName = ituffObject.getFileName();
                    if (fileName.toLowerCase().startsWith("comment")) {
                        continue;
                    }
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

    public void removeAll() {
        if (connectToHost()) {
            for (ItuffObject ituffObject : ituffs) {
                String fileName = ituffObject.getFileName();
                if (fileName.toLowerCase().startsWith("comment")) {
                    continue;
                }
                fileName = Main.auth.getProperty("remoteFolder") + fileName;
                try {
                    sftpChannel.rm(fileName);
                } catch (SftpException e) {
                    System.out.println(e.getMessage() + " " + e.getCause());
                }
            }
            disconnect();
        }
    }

}



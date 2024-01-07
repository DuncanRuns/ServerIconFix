package me.duncanruns.servericonfix;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        Set<Path> instancePaths = new HashSet<>();


        // Collect instances from Julti profiles
        Path profilesFolder = getJultiDir().resolve("profiles");
        String[] profileNames = profilesFolder.toFile().list();
        if (profileNames != null) {
            for (String name : profileNames) {
                Path profile = profilesFolder.resolve(name);
                try {
                    new Gson().fromJson(readFile(profile), JsonObject.class).getAsJsonArray("instancePaths").forEach(j -> instancePaths.add(Paths.get(j.getAsString()).toAbsolutePath()));
                } catch (Exception ignored) {
                }
            }
        }

        // Find more MultiMC instances from instances found from Julti
        for (Path instancePath : instancePaths) {
            if (Files.exists(instancePath.resolveSibling("instance.cfg"))) {
                Path instancesFolder = instancePath.getParent().getParent();
                addInstancesFromInstanceFolderToPaths(instancesFolder, instancePaths);
                break;
            }
        }

        // If finding from Julti failed, do a big search
        if (instancePaths.isEmpty()) {
            searchForMultiMCInstancesAndAdd(instancePaths);
        }

        // If all fails, ask for user to specify instances folder
        if (instancePaths.isEmpty()) {
            askForInstancesFolderAndAdd(instancePaths);
        }

        instancePaths.removeIf(path -> !Files.isDirectory(path));

        StringBuilder message = new StringBuilder("Add server-icon.png to these instances?");
        int i = 0;
        for (Path instancePath : instancePaths.stream().sorted().collect(Collectors.toList())) {
            message.append("\n").append(instancePath);
            if (++i == 20 && instancePaths.size() > 20) {
                message.append("\nand ").append(instancePaths.size() - 20).append(" others...");
                break;
            }
        }

        if (0 != JOptionPane.showConfirmDialog(null, message, "ServerIconFix", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE))
            return;

        i = 0;
        for (Path instancePath : instancePaths) {
            if (!Files.isDirectory(instancePath)) {
                continue;
            }
            try {
                copyResourceToFile("/server-icon.png", instancePath.resolve("server-icon.png"));
                i++;
            } catch (IOException ignored) {
                System.out.println("Failed to copy to " + instancePath);
            }
        }
        JOptionPane.showMessageDialog(null, "server-icon.png has been added to " + i + " instances.");
    }

    private static void askForInstancesFolderAndAdd(Set<Path> instancePaths) {
        if (0 != JOptionPane.showConfirmDialog(null, "No instances have been found automatically. Browse for instances folder?", "ServerIconFix", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)) {
            return;
        }

        JFileChooser jfc = new JFileChooser();
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        jfc.setDialogTitle("ServerIconFix: Get Instances Folder");
        jfc.setAcceptAllFileFilterUsed(false);

        int val = jfc.showOpenDialog(null);
        if (val != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selectedInstancesFolder = jfc.getSelectedFile().toPath();


        if (Files.exists(selectedInstancesFolder.resolveSibling("instance.cfg"))) {
            // Selected folder is a MultiMC .minecraft dir
            addInstancesFromInstanceFolderToPaths(selectedInstancesFolder.getParent().getParent(), instancePaths);
        } else if (Files.exists(selectedInstancesFolder.resolve("instance.cfg"))) {
            // Selected folder is a MultiMC instance dir
            addInstancesFromInstanceFolderToPaths(selectedInstancesFolder.getParent(), instancePaths);
        } else {
            String[] instanceFolders = selectedInstancesFolder.toFile().list();
            Runnable showNoneFound = () -> JOptionPane.showMessageDialog(null, "Failed to find any instances from selected folder!", "ServerIconFix", JOptionPane.WARNING_MESSAGE);

            if (instanceFolders == null || instanceFolders.length == 0) {
                showNoneFound.run();
                return;
            }
            List<Path> foundPaths = Arrays.stream(instanceFolders).map(selectedInstancesFolder::resolve).map(path -> path.resolve(".minecraft")).filter(Files::isDirectory).collect(Collectors.toList());
            if (foundPaths.isEmpty()) {
                showNoneFound.run();
                return;
            }
            instancePaths.addAll(foundPaths);
        }
    }


    public static InputStream getResourceAsStream(String name) {
        return Main.class.getResourceAsStream(name);
    }

    public static void copyResourceToFile(String resourceName, Path destination) throws IOException {
        // Answer to https://stackoverflow.com/questions/10308221/how-to-copy-file-inside-jar-to-outside-the-jar
        InputStream inStream = getResourceAsStream(resourceName);
        OutputStream outStream = Files.newOutputStream(destination);
        int readBytes;
        byte[] buffer = new byte[4096];
        while ((readBytes = inStream.read(buffer)) > 0) {
            outStream.write(buffer, 0, readBytes);
        }
        inStream.close();
        outStream.close();
    }

    private static void addInstancesFromInstanceFolderToPaths(Path instancesFolder, Set<Path> instancePaths) {
        String[] instanceNames = instancesFolder.toFile().list();
        if (instanceNames == null || instanceNames.length == 0) {
            return;
        }
        Arrays.stream(instanceNames).map(instancesFolder::resolve).map(path -> path.resolve(".minecraft")).forEach(path -> {
            if (Files.exists(path)) {
                instancePaths.add(path.toAbsolutePath());
            }
        });
    }

    private static void searchForMultiMCInstancesAndAdd(Set<Path> instancePaths) {
        // Find multimc/prism executable
        List<String> appNames = Arrays.asList("multimc.exe,prismlauncher.exe".split(","));
        List<Path> possibleLocations = new ArrayList<>();
        Path userHome = Paths.get(System.getProperty("user.home"));
        possibleLocations.add(userHome.resolve("Desktop"));
        possibleLocations.add(userHome.resolve("Documents"));
        possibleLocations.add(userHome.resolve("AppData").resolve("Roaming"));
        possibleLocations.add(userHome.resolve("AppData").resolve("Local").resolve("Programs"));
        possibleLocations.add(userHome.resolve("Downloads"));
        possibleLocations.add(Paths.get("C:\\"));
        List<Path> candidates = new ArrayList<>();
        for (Path possibleLocation : possibleLocations) {
            String[] names = possibleLocation.toFile().list();
            if (names == null) {
                continue;
            }
            for (String name : names) {
                Path toCheck = possibleLocation.resolve(name);
                if (toCheck.toFile().isFile() && appNames.contains(name.toLowerCase())) {
                    candidates.add(toCheck);
                } else if (toCheck.toFile().exists() && toCheck.toFile().isDirectory()) {
                    String[] names2 = toCheck.toFile().list();
                    if (names2 == null) {
                        continue;
                    }
                    for (String name2 : names2) {
                        Path toCheck2 = toCheck.resolve(name2);
                        if (toCheck2.toFile().isFile() && appNames.contains(name2.toLowerCase())) {
                            candidates.add(toCheck2);
                        }
                    }
                }
            }
        }

        for (Path candidate : candidates) {
            // Add instances directly installed
            addInstancesFromInstanceFolderToPaths(candidate.resolveSibling("instances"), instancePaths);

            Path configPath = candidate.resolveSibling(candidate.getFileName().toString().split("\\.")[0].toLowerCase() + ".cfg");
            try {
                String configContents = readFile(configPath);
                Arrays.stream(configContents.split("\n")).map(String::trim).filter(s -> s.startsWith("InstanceDir=")).map(s -> s.substring(12)).forEach(s -> {
                    addInstancesFromInstanceFolderToPaths(Paths.get(s), instancePaths);
                });
            } catch (Exception ignored) {
            }
        }
    }

    private static String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path));
    }

    public static Path getJultiDir() {
        return Paths.get(System.getProperty("user.home")).resolve(".Julti").toAbsolutePath();
    }
}
